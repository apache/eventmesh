package jqer

// Options config option
type Options struct {
	WrapBegin          string
	WrapLeftSeparator  string
	WrapRightSeparator string
}

// Option represents the optional function.
type Option func(opts *Options)

// WithWrapBegin config wrap begin option
func WithWrapBegin(wrapBegin string) Option {
	return func(opts *Options) {
		opts.WrapBegin = wrapBegin
	}
}

// WithWrapLeftSeparator config wrap left separator option
func WithWrapLeftSeparator(wrapBegin string) Option {
	return func(opts *Options) {
		opts.WrapBegin = wrapBegin
	}
}

// WithWrapRightSeparator config wrap right separator option
func WithWrapRightSeparator(wrapBegin string) Option {
	return func(opts *Options) {
		opts.WrapBegin = wrapBegin
	}
}

func loadOptions(options ...Option) *Options {
	var opts = new(Options)
	for _, option := range options {
		option(opts)
	}
	return opts
}
