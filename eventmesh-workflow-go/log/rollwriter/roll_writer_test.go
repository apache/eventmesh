package rollwriter

import (
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"testing"
	"time"

	rotatelogs "github.com/lestrrat-go/file-rotatelogs"
	"github.com/natefinch/lumberjack"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

// functional test
// go test -v -cover
// benchmark test
// go test -bench=. -benchtime=20s -run=Bench

const (
	testTimes    = 100000
	testRoutines = 256

	logDirTest  = "/tmp/log_test"
	logDirBench = "/tmp/log_bench"
	logDirAsync = "/tmp/log_async_test"
)

var dirs = []string{
	logDirTest,
	logDirBench,
	logDirAsync,
}

func TestMain(m *testing.M) {
	cleanup()
	ret := m.Run()

	cleanup()
	os.Exit(ret)
}

func cleanup() {
	for _, d := range dirs {
		os.RemoveAll(d)
	}
}

func TestRollWriter(t *testing.T) {
	logDir := logDirTest

	// empty file name.
	t.Run("empty_log_name", func(t *testing.T) {
		_, err := NewRollWriter("")
		assert.Error(t, err, "NewRollWriter: invalid log path")
	})

	// no rolling.
	t.Run("roll_by_default", func(t *testing.T) {
		logName := "test.log"
		w, err := NewRollWriter(filepath.Join(logDir, logName))
		assert.NoError(t, err, "NewRollWriter: create logger ok")
		log.SetOutput(w)
		for i := 0; i < testTimes; i++ {
			log.Printf("this is a test log: %d\n", i)
		}
		_ = w.Close()

		// check number of rolling log files(current files + backup files).
		time.Sleep(20 * time.Millisecond)
		logFiles := getLogBackups(logDir, logName)
		if len(logFiles) != 1 {
			t.Errorf("Number of log backup files should be 1")
		}
	})

	// roll by size.
	t.Run("roll_by_size", func(t *testing.T) {
		logName := "test_size.log"
		w, err := NewRollWriter(filepath.Join(logDir, logName),
			WithMaxSize(1),
			WithMaxAge(1),
			WithMaxBackups(2),
		)
		assert.NoError(t, err, "NewRollWriter: create logger ok")
		log.SetOutput(w)
		for i := 0; i < testTimes; i++ {
			log.Printf("this is a test log: %d\n", i)
		}
		_ = w.Close()

		// check number of rolling log files.
		time.Sleep(20 * time.Millisecond)
		logFiles := getLogBackups(logDir, logName)
		if len(logFiles) != 3 {
			t.Errorf("Number of log backup files should be 3")
		}

		// check rolling log file size(allow to exceed a little).
		for _, file := range logFiles {
			if file.Size() > 1*1024*1024+1024 {
				t.Errorf("Log file size exceeds max_size")
			}
		}
	})

	// rolling by time.
	t.Run("roll_by_time", func(t *testing.T) {
		logName := "test_time.log"
		w, err := NewRollWriter(filepath.Join(logDir, logName),
			WithRotationTime(".%Y%m%d"),
			WithMaxSize(1),
			WithMaxAge(1),
			WithMaxBackups(3),
			WithCompress(true),
		)
		assert.NoError(t, err, "NewRollWriter: create logger ok")
		log.SetOutput(w)
		for i := 0; i < testTimes; i++ {
			log.Printf("this is a test log: %d\n", i)
		}
		_ = w.Close()

		// check number of rolling log files.
		time.Sleep(20 * time.Millisecond)
		logFiles := getLogBackups(logDir, logName)
		if len(logFiles) != 4 {
			t.Errorf("Number of log files should be 4")
		}

		// check rolling log file size(allow to exceed a little).
		for _, file := range logFiles {
			if file.Size() > 1*1024*1024+1024 {
				t.Errorf("Log file size exceeds max_size")
			}
		}

		// check number of compressed files.
		compressFileNum := 0
		for _, file := range logFiles {
			if strings.HasSuffix(file.Name(), compressSuffix) {
				compressFileNum++
			}
		}
		if compressFileNum != 3 {
			t.Errorf("Number of compress log files should be 3")
		}
	})

	// wait 1 second.
	time.Sleep(1 * time.Second)

	// print log file list.
	printLogFiles(logDir)
}

func TestAsyncRollWriter(t *testing.T) {
	logDir := logDirAsync
	const flushThreshold = 4 * 1024

	// no rolling(asynchronous mod)
	t.Run("roll_by_default_async", func(t *testing.T) {
		logName := "test.log"
		w, err := NewRollWriter(filepath.Join(logDir, logName))
		assert.NoError(t, err, "NewRollWriter: create logger ok")

		asyncWriter := NewAsyncRollWriter(w, WithLogQueueSize(10), WithWriteLogSize(1024),
			WithWriteLogInterval(100), WithDropLog(true))
		log.SetOutput(asyncWriter)
		for i := 0; i < testTimes; i++ {
			log.Printf("this is a test log: %d\n", i)
		}
		_ = asyncWriter.Close()

		// check number of rolling log files.
		time.Sleep(20 * time.Millisecond)
		logFiles := getLogBackups(logDir, logName)
		if len(logFiles) != 1 {
			t.Errorf("Number of log backup files should be 1")
		}
	})

	// rolling by size(asynchronous mod)
	t.Run("roll_by_size_async", func(t *testing.T) {
		logName := "test_size.log"
		w, err := NewRollWriter(filepath.Join(logDir, logName),
			WithMaxSize(1),
			WithMaxAge(1),
		)
		assert.NoError(t, err, "NewRollWriter: create logger ok")

		asyncWriter := NewAsyncRollWriter(w, WithWriteLogSize(flushThreshold))
		log.SetOutput(asyncWriter)
		for i := 0; i < testTimes; i++ {
			log.Printf("this is a test log: %d\n", i)
		}
		_ = asyncWriter.Close()

		// check number of rolling log files.
		time.Sleep(20 * time.Millisecond)
		logFiles := getLogBackups(logDir, logName)
		if len(logFiles) != 5 {
			t.Errorf("Number of log backup files should be 5")
		}

		// check rolling log file size(asynchronous mod, may exceed 4K at most)
		for _, file := range logFiles {
			if file.Size() > 1*1024*1024+flushThreshold*2 {
				t.Errorf("Log file size exceeds max_size")
			}
		}
	})

	// rolling by time(asynchronous mod)
	t.Run("roll_by_time_async", func(t *testing.T) {
		logName := "test_time.log"
		w, err := NewRollWriter(filepath.Join(logDir, logName),
			WithRotationTime(".%Y%m%d"),
			WithMaxSize(1),
			WithMaxAge(1),
			WithCompress(true),
		)
		assert.NoError(t, err, "NewRollWriter: create logger ok")

		asyncWriter := NewAsyncRollWriter(w, WithWriteLogSize(flushThreshold))
		log.SetOutput(asyncWriter)
		for i := 0; i < testTimes; i++ {
			log.Printf("this is a test log: %d\n", i)
		}
		_ = asyncWriter.Close()

		// check number of rolling log files.
		time.Sleep(20 * time.Millisecond)
		logFiles := getLogBackups(logDir, logName)
		if len(logFiles) != 5 {
			t.Errorf("Number of log files should be 5")
		}

		// check rolling log file size(asynchronous, may exceed 4K at most)
		for _, file := range logFiles {
			if file.Size() > 1*1024*1024+flushThreshold*2 {
				t.Errorf("Log file size exceeds max_size")
			}
		}

		// number of compressed files.
		compressFileNum := 0
		for _, file := range logFiles {
			if strings.HasSuffix(file.Name(), compressSuffix) {
				compressFileNum++
			}
		}
		if compressFileNum != 4 {
			t.Errorf("Number of compress log files should be 4")
		}
	})

	// wait 1 second.
	time.Sleep(1 * time.Second)

	// print log file list.
	printLogFiles(logDir)
}

func TestRollWriterRace(t *testing.T) {
	logDir := filepath.Join(logDirBench, "rollwriter_race")

	writer, _ := NewRollWriter(
		filepath.Join(logDir, "test.log"),
		WithRotationTime(".%Y%m%d"),
	)
	writer.opts.MaxSize = 1

	wg := sync.WaitGroup{}
	wg.Add(2)
	go func() {
		defer wg.Done()
		for i := 0; i < 100; i++ {
			writer.Write([]byte(fmt.Sprintf("this is a test log: 1-%d\n", i)))
		}
	}()

	go func() {
		defer wg.Done()
		for i := 0; i < 100; i++ {
			writer.Write([]byte(fmt.Sprintf("this is a test log: 2-%d\n", i)))
		}
	}()
	wg.Wait()

	_ = writer.Close()
	time.Sleep(20 * time.Millisecond)
	ret := execCommand("/bin/bash", "-c", "cat "+logDir+"/*|wc -l")
	assert.NotNil(t, ret)
}

func TestAsyncRollWriterRace(t *testing.T) {
	// clean up test files lest over from last time.
	logDir := filepath.Join(logDirBench, "async_rollwriter_race")

	writer, _ := NewRollWriter(
		filepath.Join(logDir, "test.log"),
		WithRotationTime(".%Y%m%d"),
	)
	writer.opts.MaxSize = 1
	w := NewAsyncRollWriter(writer)

	wg := sync.WaitGroup{}
	wg.Add(2)
	go func() {
		defer wg.Done()
		for i := 0; i < 100; i++ {
			w.Write([]byte(fmt.Sprintf("this is a test log: 1-%d\n", i)))
		}
	}()

	go func() {
		defer wg.Done()
		for i := 0; i < 100; i++ {
			w.Write([]byte(fmt.Sprintf("this is a test log: 2-%d\n", i)))
		}
	}()
	wg.Wait()

	_ = w.Close()
	time.Sleep(20 * time.Millisecond)
	ret := execCommand("/bin/bash", "-c", "cat "+logDir+"/*|wc -l")
	assert.NotNil(t, ret)
}

func TestAsyncRollWriterSyncTwice(t *testing.T) {
	w := NewAsyncRollWriter(&noopWriteCloser{})
	w.Write([]byte("hello"))
	require.Nil(t, w.Sync())
	require.Nil(t, w.Sync())
	require.Nil(t, w.Close())
}

type noopWriteCloser struct{}

func (*noopWriteCloser) Write(p []byte) (n int, err error) { return }

func (*noopWriteCloser) Close() (err error) { return }

// BenchmarkRollWriterBySize benchmarks RollWriter by size.
func BenchmarkRollWriterBySize(b *testing.B) {
	logDir := filepath.Join(logDirBench, "rollwriter_bysize")

	// init RollWriter.
	writer, _ := NewRollWriter(filepath.Join(logDir, "test.log"))
	core := zapcore.NewCore(
		zapcore.NewConsoleEncoder(zap.NewProductionEncoderConfig()),
		zapcore.AddSync(writer),
		zapcore.DebugLevel,
	)
	logger := zap.New(
		core,
		zap.AddCaller(),
	)

	// warm up.
	for i := 0; i < testTimes; i++ {
		logger.Debug(fmt.Sprint("this is a test log: ", i))
	}

	b.SetParallelism(testRoutines / runtime.GOMAXPROCS(0))
	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			logger.Debug("this is a test log")
		}
	})
}

// BenchmarkRollWriterByTime benchmarks RollWriter by time.
func BenchmarkRollWriterByTime(b *testing.B) {
	logDir := filepath.Join(logDirBench, "rollwriter_bytime")

	// init RollWriter.
	writer, _ := NewRollWriter(
		filepath.Join(logDir, "test.log"),
		WithRotationTime(".%Y%m%d"),
	)
	core := zapcore.NewCore(
		zapcore.NewConsoleEncoder(zap.NewProductionEncoderConfig()),
		zapcore.AddSync(writer),
		zapcore.DebugLevel,
	)
	logger := zap.New(
		core,
		zap.AddCaller(),
	)

	// warm up.
	for i := 0; i < testTimes; i++ {
		logger.Debug(fmt.Sprint("this is a test log: ", i))
	}

	b.SetParallelism(testRoutines / runtime.GOMAXPROCS(0))
	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			logger.Debug("this is a test log")
		}
	})
}

// BenchmarkAsyncRollWriterBySize benchmarks asynchronous RollWriter.
func BenchmarkAsyncRollWriterBySize(b *testing.B) {
	logDir := filepath.Join(logDirBench, "async_rollwriter_bysize")

	// init RollWriter.
	writer, _ := NewRollWriter(
		filepath.Join(logDir, "test.log"),
	)
	asyncWriter := NewAsyncRollWriter(writer)
	core := zapcore.NewCore(
		zapcore.NewConsoleEncoder(zap.NewProductionEncoderConfig()),
		asyncWriter,
		zapcore.DebugLevel,
	)
	logger := zap.New(
		core,
		zap.AddCaller(),
	)

	// warm up.
	for i := 0; i < testTimes; i++ {
		logger.Debug(fmt.Sprint("this is a test log: ", i))
	}

	b.SetParallelism(testRoutines / runtime.GOMAXPROCS(0))
	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			logger.Debug("this is a test log")
		}
	})
}

// BenchmarkAsyncRollWriterByTime benchmarks asynchronous RollWriter by time.
func BenchmarkAsyncRollWriterByTime(b *testing.B) {
	logDir := filepath.Join(logDirBench, "async_rollwriter_bytime")

	// init RollWriter.
	writer, _ := NewRollWriter(
		filepath.Join(logDir, "test.log"),
		WithRotationTime(".%Y%m%d"),
	)
	asyncWriter := NewAsyncRollWriter(writer)
	core := zapcore.NewCore(
		zapcore.NewConsoleEncoder(zap.NewProductionEncoderConfig()),
		asyncWriter,
		zapcore.DebugLevel,
	)
	logger := zap.New(
		core,
		zap.AddCaller(),
	)

	// warm up.
	for i := 0; i < testTimes; i++ {
		logger.Debug(fmt.Sprint("this is a test log: ", i))
	}

	b.SetParallelism(testRoutines / runtime.GOMAXPROCS(0))
	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			logger.Debug("this is a test log")
		}
	})
}

// BenchmarkLumberjack benchmarks lumberjack.
func BenchmarkLumberjack(b *testing.B) {
	logDir := filepath.Join(logDirBench, "lumberjack")

	// init Logger.
	writer := &lumberjack.Logger{
		Filename:   filepath.Join(logDir, "test.log"),
		MaxSize:    100 * 1024 * 1024,
		MaxBackups: 0,
		MaxAge:     0,
		Compress:   false,
	}
	core := zapcore.NewCore(
		zapcore.NewConsoleEncoder(zap.NewProductionEncoderConfig()),
		zapcore.AddSync(writer),
		zapcore.DebugLevel,
	)
	logger := zap.New(
		core,
		zap.AddCaller(),
	)
	// warm up.
	for i := 0; i < testTimes; i++ {
		logger.Debug(fmt.Sprint("this is a test log: ", i))
	}

	b.SetParallelism(testRoutines / runtime.GOMAXPROCS(0))
	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			logger.Debug("this is a test log")
		}
	})
}

// BenchmarkRotatelogs benchmarks rotate logs.
func BenchmarkRotatelogs(b *testing.B) {
	logDir := filepath.Join(logDirBench, "rotatelogs")

	// init logs.
	writer, _ := rotatelogs.New(
		filepath.Join(logDir, "test.log")+".%Y%m%d",
		rotatelogs.WithMaxAge(0),
		rotatelogs.WithRotationTime(24*time.Hour),
	)
	core := zapcore.NewCore(
		zapcore.NewConsoleEncoder(zap.NewProductionEncoderConfig()),
		zapcore.AddSync(writer),
		zapcore.DebugLevel,
	)
	logger := zap.New(
		core,
		zap.AddCaller(),
	)
	// warm up.
	for i := 0; i < testTimes; i++ {
		logger.Debug(fmt.Sprint("this is a test log: ", i))
	}

	b.SetParallelism(testRoutines / runtime.GOMAXPROCS(0))
	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			logger.Debug("this is a test log")
		}
	})
}

func printLogFiles(logDir string) {
	fmt.Println("================================================")
	fmt.Printf("[%s]:\n", logDir)
	files, err := ioutil.ReadDir(logDir)
	if err != nil {
		fmt.Println("ReadDir failed ", err)
		return
	}
	for _, file := range files {
		fmt.Println("\t", file.Name(), file.Size())
	}
}

func execCommand(name string, args ...string) string {
	fmt.Println(name, args)
	cmd := exec.Command(name, args...)
	output, err := cmd.Output()
	if err != nil {
		fmt.Printf("exec command failed, err: %v\n", err)
	}
	fmt.Println(string(output))

	return string(output)
}

func getLogBackups(logDir string, prefix string) []os.FileInfo {
	logFiles := []os.FileInfo{}
	files, err := ioutil.ReadDir(logDir)
	if err != nil {
		return logFiles
	}

	for _, file := range files {
		if !strings.HasPrefix(file.Name(), prefix) {
			continue
		}

		logFiles = append(logFiles, file)
	}

	return logFiles
}
