/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "rmb_define.h"
#define min(x,y) ({\
				                typeof(x) _x = (x);\
				                typeof(y) _y = (y);\
				                (void)(&_x == &_y);\
				                _x < _y ? _x : _y;})
#define max(x,y) ({\
				                typeof(x) _x = (x);\
				                typeof(y) _y = (y);\
				                (void)(&_x == &_y);\
				                _x > _y ? _x : _y;})
/*
 * internal helper to calculate the unused elements in a fifo
 */
static inline unsigned int wemq_kfifo_unused(struct __wemq_kfifo *fifo)
{
	return (fifo->mask + 1) - (fifo->in - fifo->out);
}

int __wemq_kfifo_alloc(struct __wemq_kfifo *fifo, unsigned int size,
		size_t esize)
{
	/*
	 * round down to the next power of 2, since our 'let the indices
	 * wrap' technique works only in this case.
	 */
	size = roundup_pow_of_two(size);

	fifo->in = 0;
	fifo->out = 0;
	fifo->esize = esize;

	if (size < 2) {
		fifo->data = NULL;
		fifo->mask = 0;
		return -KFIFO_EINVAL;
	}

	fifo->data = malloc(size * esize);

	if (!fifo->data) {
		fifo->mask = 0;
		return -KFIFO_ENOMEM;
	}
	fifo->mask = size - 1;

	return 0;
}
////EXPORT_SYMBOL(__wemq_kfifo_alloc);

void __wemq_kfifo_free(struct __wemq_kfifo *fifo)
{
	free(fifo->data);
	fifo->in = 0;
	fifo->out = 0;
	fifo->esize = 0;
	fifo->data = NULL;
	fifo->mask = 0;
}
////EXPORT_SYMBOL(__wemq_kfifo_free);

int __wemq_kfifo_init(struct __wemq_kfifo *fifo, void *buffer,
		unsigned int size, size_t esize)
{
	size /= esize;

	size = roundup_pow_of_two(size);

	fifo->in = 0;
	fifo->out = 0;
	fifo->esize = esize;
	fifo->data = buffer;

	if (size < 2) {
		fifo->mask = 0;
		return -KFIFO_EINVAL;
	}
	fifo->mask = size - 1;

	return 0;
}
//EXPORT_SYMBOL(__wemq_kfifo_init);

static void wemq_kfifo_copy_in(struct __wemq_kfifo *fifo, const void *src,
		unsigned int len, unsigned int off)
{
	unsigned int size = fifo->mask + 1;
	unsigned int esize = fifo->esize;
	unsigned int l;

	off &= fifo->mask;
	if (esize != 1) {
		off *= esize;
		size *= esize;
		len *= esize;
	}
	l = min(len, size - off);

	memcpy(fifo->data + off, src, l);
	memcpy(fifo->data, src + l, len - l);
	/*
	 * make sure that the data in the fifo is up to date before
	 * incrementing the fifo->in index counter
	 */
	//smp_wmb();
}

unsigned int __wemq_kfifo_in(struct __wemq_kfifo *fifo,
		const void *buf, unsigned int len)
{
	unsigned int l;

	l = wemq_kfifo_unused(fifo);
	if (len > l)
		len = l;

	wemq_kfifo_copy_in(fifo, buf, len, fifo->in);
	fifo->in += len;
	return len;
}
//EXPORT_SYMBOL(__wemq_kfifo_in);

static void wemq_kfifo_copy_out(struct __wemq_kfifo *fifo, void *dst,
		unsigned int len, unsigned int off)
{
	unsigned int size = fifo->mask + 1;
	unsigned int esize = fifo->esize;
	unsigned int l;

	off &= fifo->mask;
	if (esize != 1) {
		off *= esize;
		size *= esize;
		len *= esize;
	}
	l = min(len, size - off);

	memcpy(dst, fifo->data + off, l);
	memcpy(dst + l, fifo->data, len - l);
	/*
	 * make sure that the data is copied before
	 * incrementing the fifo->out index counter
	 */
	//smp_wmb();
}

unsigned int __wemq_kfifo_out_peek(struct __wemq_kfifo *fifo,
		void *buf, unsigned int len)
{
	unsigned int l;

	l = fifo->in - fifo->out;
	if (len > l)
		len = l;

	wemq_kfifo_copy_out(fifo, buf, len, fifo->out);
	return len;
}
//EXPORT_SYMBOL(__wemq_kfifo_out_peek);

unsigned int __wemq_kfifo_out(struct __wemq_kfifo *fifo,
		void *buf, unsigned int len)
{
	len = __wemq_kfifo_out_peek(fifo, buf, len);
	fifo->out += len;
	return len;
}
//EXPORT_SYMBOL(__wemq_kfifo_out);

unsigned int __wemq_kfifo_max_r(unsigned int len, size_t recsize)
{
	unsigned int max = (1 << (recsize << 3)) - 1;

	if (len > max)
		return max;
	return len;
}
//EXPORT_SYMBOL(__wemq_kfifo_max_r);

#define	__WEMQ_KFIFO_PEEK(data, out, mask) \
	((data)[(out) & (mask)])
/*
 * __wemq_kfifo_peek_n internal helper function for determinate the length of
 * the next record in the fifo
 */
static unsigned int __wemq_kfifo_peek_n(struct __wemq_kfifo *fifo, size_t recsize)
{
	unsigned int l;
	unsigned int mask = fifo->mask;
	unsigned char *data = fifo->data;

	l = __WEMQ_KFIFO_PEEK(data, fifo->out, mask);

	if (--recsize)
		l |= __WEMQ_KFIFO_PEEK(data, fifo->out + 1, mask) << 8;

	return l;
}

#define	__WEMQ_KFIFO_POKE(data, in, mask, val) \
	( \
	(data)[(in) & (mask)] = (unsigned char)(val) \
	)

/*
 * __wemq_kfifo_poke_n internal helper function for storeing the length of
 * the record into the fifo
 */
static void __wemq_kfifo_poke_n(struct __wemq_kfifo *fifo, unsigned int n, size_t recsize)
{
	unsigned int mask = fifo->mask;
	unsigned char *data = fifo->data;

	__WEMQ_KFIFO_POKE(data, fifo->in, mask, n);

	if (recsize > 1)
		__WEMQ_KFIFO_POKE(data, fifo->in + 1, mask, n >> 8);
}

unsigned int __wemq_kfifo_len_r(struct __wemq_kfifo *fifo, size_t recsize)
{
	return __wemq_kfifo_peek_n(fifo, recsize);
}
//EXPORT_SYMBOL(__wemq_kfifo_len_r);

unsigned int __wemq_kfifo_in_r(struct __wemq_kfifo *fifo, const void *buf,
		unsigned int len, size_t recsize)
{
	if (len + recsize > wemq_kfifo_unused(fifo))
		return 0;

	__wemq_kfifo_poke_n(fifo, len, recsize);

	wemq_kfifo_copy_in(fifo, buf, len, fifo->in + recsize);
	fifo->in += len + recsize;
	return len;
}
//EXPORT_SYMBOL(__wemq_kfifo_in_r);

static unsigned int wemq_kfifo_out_copy_r(struct __wemq_kfifo *fifo,
	void *buf, unsigned int len, size_t recsize, unsigned int *n)
{
	*n = __wemq_kfifo_peek_n(fifo, recsize);

	if (len > *n)
		len = *n;

	wemq_kfifo_copy_out(fifo, buf, len, fifo->out + recsize);
	return len;
}

unsigned int __wemq_kfifo_out_peek_r(struct __wemq_kfifo *fifo, void *buf,
		unsigned int len, size_t recsize)
{
	unsigned int n;

	if (fifo->in == fifo->out)
		return 0;

	return wemq_kfifo_out_copy_r(fifo, buf, len, recsize, &n);
}
//EXPORT_SYMBOL(__wemq_kfifo_out_peek_r);

unsigned int __wemq_kfifo_out_r(struct __wemq_kfifo *fifo, void *buf,
		unsigned int len, size_t recsize)
{
	unsigned int n;

	if (fifo->in == fifo->out)
		return 0;

	len = wemq_kfifo_out_copy_r(fifo, buf, len, recsize, &n);
	fifo->out += n + recsize;
	return len;
}
//EXPORT_SYMBOL(__wemq_kfifo_out_r);

void __wemq_kfifo_skip_r(struct __wemq_kfifo *fifo, size_t recsize)
{
	unsigned int n;

	n = __wemq_kfifo_peek_n(fifo, recsize);
	fifo->out += n + recsize;
}
//EXPORT_SYMBOL(__wemq_kfifo_skip_r);



