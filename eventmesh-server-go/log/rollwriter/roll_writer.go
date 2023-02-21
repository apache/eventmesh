// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package rollwriter provides a high performance rolling file log.
// It can coordinate with any logs which depends on io.Writer, such as golang standard log.
// Main features:
//  1. support rolling logs by file size.
//  2. support rolling logs by datetime.
//  3. support scavenging expired or useless logs.
//  4. support compressing logs.
package rollwriter

import (
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/lestrrat-go/strftime"
)

const (
	backupTimeFormat = "bk-20060102-150405.00000"
	compressSuffix   = ".gz"
)

// ensure we always implement io.WriteCloser.
var _ io.WriteCloser = (*RollWriter)(nil)

// RollWriter is a file log writer which support rolling by size or datetime.
// It implements io.WriteCloser.
type RollWriter struct {
	filePath string
	opts     *Options

	pattern  *strftime.Strftime
	currDir  string
	currPath string
	currSize int64
	currFile atomic.Value
	openTime int64

	mu         sync.Mutex
	notifyOnce sync.Once
	notifyCh   chan bool
	closeOnce  sync.Once
	closeCh    chan *os.File
}

// NewRollWriter creates a new RollWriter.
func NewRollWriter(filePath string, opt ...Option) (*RollWriter, error) {
	opts := &Options{
		MaxSize:    0,     // default no rolling by file size
		MaxAge:     0,     // default no scavenging on expired logs
		MaxBackups: 0,     // default no scavenging on redundant logs
		Compress:   false, // default no compressing
	}

	// opt has the highest priority and should overwrite the original one.
	for _, o := range opt {
		o(opts)
	}

	if filePath == "" {
		return nil, errors.New("invalid file path")
	}

	pattern, err := strftime.New(filePath + opts.TimeFormat)
	if err != nil {
		return nil, errors.New("invalid time pattern")
	}

	w := &RollWriter{
		filePath: filePath,
		opts:     opts,
		pattern:  pattern,
		currDir:  filepath.Dir(filePath),
	}

	if err := os.MkdirAll(w.currDir, 0755); err != nil {
		return nil, err
	}

	return w, nil
}

// Write writes logs. It implements io.Writer.
func (w *RollWriter) Write(v []byte) (n int, err error) {
	// reopen file every 10 seconds.
	if w.getCurrFile() == nil || time.Now().Unix()-atomic.LoadInt64(&w.openTime) > 10 {
		w.mu.Lock()
		w.reopenFile()
		w.mu.Unlock()
	}

	// return when failed to open the file.
	if w.getCurrFile() == nil {
		return 0, errors.New("open file fail")
	}

	// write logs to file.
	n, err = w.getCurrFile().Write(v)
	atomic.AddInt64(&w.currSize, int64(n))

	// rolling on full
	if w.opts.MaxSize > 0 && atomic.LoadInt64(&w.currSize) >= w.opts.MaxSize {
		w.mu.Lock()
		w.backupFile()
		w.mu.Unlock()
	}
	return n, err
}

// Close closes the current log file. It implements io.Closer.
func (w *RollWriter) Close() error {
	if w.getCurrFile() == nil {
		return nil
	}
	err := w.getCurrFile().Close()
	w.setCurrFile(nil)

	if w.notifyCh != nil {
		close(w.notifyCh)
		w.notifyCh = nil
	}

	if w.closeCh != nil {
		close(w.closeCh)
		w.closeCh = nil
	}

	return err
}

// getCurrFile returns the current log file.
func (w *RollWriter) getCurrFile() *os.File {
	if file, ok := w.currFile.Load().(*os.File); ok {
		return file
	}
	return nil
}

// setCurrFile sets the current log file.
func (w *RollWriter) setCurrFile(file *os.File) {
	w.currFile.Store(file)
}

// reopenFile reopen the file regularly. It notifies the scavenger if file path has changed.
func (w *RollWriter) reopenFile() {
	if w.getCurrFile() == nil || time.Now().Unix()-atomic.LoadInt64(&w.openTime) > 10 {
		atomic.StoreInt64(&w.openTime, time.Now().Unix())
		currPath := w.pattern.FormatString(time.Now())
		if w.currPath != currPath {
			w.currPath = currPath
			w.notify()
		}
		_ = w.doReopenFile(w.currPath)
	}
}

// doReopenFile reopen the file.
func (w *RollWriter) doReopenFile(path string) error {
	atomic.StoreInt64(&w.openTime, time.Now().Unix())
	lastFile := w.getCurrFile()
	of, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0666)
	if err == nil {
		w.setCurrFile(of)
		if lastFile != nil {
			// delay closing until not used.
			w.delayCloseFile(lastFile)
		}
		st, _ := os.Stat(path)
		if st != nil {
			atomic.StoreInt64(&w.currSize, st.Size())
		}
	}
	return err
}

// backupFile backs this file up and reopen a new one if file size is too large.
func (w *RollWriter) backupFile() {
	if w.opts.MaxSize > 0 && atomic.LoadInt64(&w.currSize) >= w.opts.MaxSize {
		atomic.StoreInt64(&w.currSize, 0)

		// rename the old file.
		newName := w.currPath + "." + time.Now().Format(backupTimeFormat)
		if _, e := os.Stat(w.currPath); !os.IsNotExist(e) {
			_ = os.Rename(w.currPath, newName)
		}

		// reopen a new one.
		_ = w.doReopenFile(w.currPath)
		w.notify()
	}
}

// notify runs scavengers.
func (w *RollWriter) notify() {
	w.notifyOnce.Do(func() {
		w.notifyCh = make(chan bool, 1)
		go w.runCleanFiles()
	})
	select {
	case w.notifyCh <- true:
	default:
	}
}

// runCleanFiles cleans redundant or expired (compressed) logs in a new goroutine.
func (w *RollWriter) runCleanFiles() {
	for range w.notifyCh {
		if w.opts.MaxBackups == 0 && w.opts.MaxAge == 0 && !w.opts.Compress {
			continue
		}
		w.cleanFiles()
	}
}

// delayCloseFile delay closing file
func (w *RollWriter) delayCloseFile(file *os.File) {
	w.closeOnce.Do(func() {
		w.closeCh = make(chan *os.File, 100)
		go w.runCloseFiles()
	})
	w.closeCh <- file
}

// runCloseFiles delay closing file in a new goroutine.
func (w *RollWriter) runCloseFiles() {
	for f := range w.closeCh {
		// delay 20ms
		time.Sleep(20 * time.Millisecond)
		f.Close()
	}
}

// cleanFiles cleans redundant or expired (compressed) logs.
func (w *RollWriter) cleanFiles() {
	// get the file list of current log.
	files, err := w.getOldLogFiles()
	if err != nil || len(files) == 0 {
		return
	}

	// find the oldest files to scavenge.
	var compress, remove []logInfo
	files = filterByMaxBackups(files, &remove, w.opts.MaxBackups)

	// find the expired files by last modified time.
	files = filterByMaxAge(files, &remove, w.opts.MaxAge)

	// find files to compress by file extension .gz.
	filterByCompressExt(files, &compress, w.opts.Compress)

	// delete expired or redundant files.
	w.removeFiles(remove)

	// compress log files.
	w.compressFiles(compress)
}

// getOldLogFiles returns the log file list ordered by modified time.
func (w *RollWriter) getOldLogFiles() ([]logInfo, error) {
	files, err := ioutil.ReadDir(w.currDir)
	if err != nil {
		return nil, fmt.Errorf("can't read log file directory: %s", err)
	}
	logFiles := []logInfo{}
	filename := filepath.Base(w.filePath)
	for _, f := range files {
		if f.IsDir() {
			continue
		}

		if modTime, err := w.matchLogFile(f.Name(), filename); err == nil {
			logFiles = append(logFiles, logInfo{modTime, f})
		}
	}
	sort.Sort(byFormatTime(logFiles))
	return logFiles, nil
}

// matchLogFile checks whether current log file matches all relative log files, if matched, returns
// the modified time.
func (w *RollWriter) matchLogFile(filename, filePrefix string) (time.Time, error) {
	// exclude current log file.
	// a.log
	// a.log.20200712
	if filepath.Base(w.currPath) == filename {
		return time.Time{}, errors.New("ignore current logfile")
	}

	// match all log files with current log file.
	// a.log -> a.log.20200712-1232/a.log.20200712-1232.gz
	// a.log.20200712 -> a.log.20200712.20200712-1232/a.log.20200712.20200712-1232.gz
	if !strings.HasPrefix(filename, filePrefix) {
		return time.Time{}, errors.New("mismatched prefix")
	}

	if st, _ := os.Stat(filepath.Join(w.currDir, filename)); st != nil {
		return st.ModTime(), nil
	}
	return time.Time{}, errors.New("file stat fail")
}

// removeFiles deletes expired or redundant log files.
func (w *RollWriter) removeFiles(remove []logInfo) {
	// clean expired or redundant files.
	for _, f := range remove {
		os.Remove(filepath.Join(w.currDir, f.Name()))
	}
}

// compressFiles compresses demanded log files.
func (w *RollWriter) compressFiles(compress []logInfo) {
	// compress log files.
	for _, f := range compress {
		fn := filepath.Join(w.currDir, f.Name())
		_ = compressFile(fn, fn+compressSuffix)
	}
}

// filterByMaxBackups filters redundant files that exceeded the limit.
func filterByMaxBackups(files []logInfo, remove *[]logInfo, maxBackups int) []logInfo {
	if maxBackups == 0 || len(files) < maxBackups {
		return files
	}
	var remaining []logInfo
	preserved := make(map[string]bool)
	for _, f := range files {
		fn := strings.TrimSuffix(f.Name(), compressSuffix)
		preserved[fn] = true

		if len(preserved) > maxBackups {
			*remove = append(*remove, f)
		} else {
			remaining = append(remaining, f)
		}
	}
	return remaining
}

// filterByMaxAge filters expired files.
func filterByMaxAge(files []logInfo, remove *[]logInfo, maxAge int) []logInfo {
	if maxAge <= 0 {
		return files
	}
	var remaining []logInfo
	diff := time.Duration(int64(24*time.Hour) * int64(maxAge))
	cutoff := time.Now().Add(-1 * diff)
	for _, f := range files {
		if f.timestamp.Before(cutoff) {
			*remove = append(*remove, f)
		} else {
			remaining = append(remaining, f)
		}
	}
	return remaining
}

// filterByCompressExt filters all compressed files.
func filterByCompressExt(files []logInfo, compress *[]logInfo, needCompress bool) {
	if !needCompress {
		return
	}
	for _, f := range files {
		if !strings.HasSuffix(f.Name(), compressSuffix) {
			*compress = append(*compress, f)
		}
	}
}

// compressFile compresses file src to dst, and removes src on success.
func compressFile(src, dst string) (err error) {
	f, err := os.Open(src)
	if err != nil {
		return fmt.Errorf("failed to open file: %v", err)
	}
	defer f.Close()

	gzf, err := os.OpenFile(dst, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0666)
	if err != nil {
		return fmt.Errorf("failed to open compressed file: %v", err)
	}
	defer gzf.Close()

	gz := gzip.NewWriter(gzf)
	defer func() {
		gz.Close()
		if err != nil {
			os.Remove(dst)
			err = fmt.Errorf("failed to compress file: %v", err)
		} else {
			os.Remove(src)
		}
	}()

	if _, err := io.Copy(gz, f); err != nil {
		return err
	}
	return nil
}

// logInfo is an assistant struct which is used to return file name and last modified time.
type logInfo struct {
	timestamp time.Time
	os.FileInfo
}

// byFormatTime sorts by time descending order.
type byFormatTime []logInfo

// Less checks whether the time of b[j] is early than the time of b[i].
func (b byFormatTime) Less(i, j int) bool {
	return b[i].timestamp.After(b[j].timestamp)
}

// Swap swaps b[i] and b[j].
func (b byFormatTime) Swap(i, j int) {
	b[i], b[j] = b[j], b[i]
}

// Len returns the length of list b.
func (b byFormatTime) Len() int {
	return len(b)
}
