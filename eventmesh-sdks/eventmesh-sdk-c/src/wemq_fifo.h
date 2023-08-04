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

/*
 * API :
 * 	DEFINE_WEMQ_KFIFO     : statically allocated FIFO object
 *	DECLARE_WEMQ_KFIFO    : declare a FIFO object
 *	DECLARE_WEMQ_KFIFO_PTR: declare a pointer whitch point to a FIFO object
 *	INIT_WEMQFIFO         : init a FIFO object
 *
 *	wemq_kfifo_len     : return the num of element that in the fifo
 *	wemq_kfifo_peek_len: return the num of bytes that in use
 *
 *	wemq_kfifo_is_empty: retruns true if the fifo is empty
 *	wemq_kfifo_is_full : returns true if the fifo is full
 *	wemq_kfifo_is_avail: returns the numer of unused elements in the fifo
 *	wemq_kfifo_skip    : skip a element in fifo
 *
 *	wemq_kfifo_alloc   : dynamically allocates a new fifo buffer
 *	wemq_kfifo_free    : frees the fifo
 *
 *	wemq_kfifo_put	  : copies the given value into the fifo (by element)
 *	wemq_kfifo_get     : reads a element from the fifo
 *	wemq_kfifo_peek    : reads a element from the fifo without removing it form the fifo
 *
 *	wemq_kfifo_in	  : copies the given buffer into the fifo and returnns the number of copied elements
 *	wemq_kfifo_out     : get some elements form the fifo and return the numbers of elements
 *	wemq_kfifo_out_peek: get the data from the fifo and return the numbers of elements copied. These elements is not removed from the fifo
 *
 */

#ifndef _WEMQ_WEMQ_KFIFO_H
#define _WEMQ_WEMQ_KFIFO_H
#ifdef __cplusplus
extern "C" {
#endif
//#include <stdlib.h>
//#include <stdint.h>
//#include <string.h>

#define __u32 uint32_t
#define __u64 uint64_t
#define __must_check __attribute__((warn_unused_result))

#define __must_be_array(arr) 0
#define ARRAY_SIZE(arr) (sizeof(arr) / sizeof((arr)[0]) + __must_be_array(arr))

enum
{
	KFIFO_EINVAL = -1,
	KFIFO_ENOMEM = -2
};

static inline int fls(int x)
{
	int r;

	__asm__("bsrl %1,%0\n\t"
			"jnz 1f\n\t"
			"movl $-1,%0\n"
			"1:" : "=r" (r) : "rm" (x));
	return r+1;
}

static inline int fls64(__u64 x)
{
	__u32 h = x >> 32; 
	if (h) 
		return fls(h) + 32; 
	return fls(x);
}

static inline unsigned fls_long(unsigned long l)
{
	if (sizeof(l) == 4)
		return fls(l);
	return fls64(l);
}

static inline unsigned long roundup_pow_of_two(unsigned long x)
{
	return 1UL << fls_long(x - 1); 
}

struct __wemq_kfifo {
	unsigned int	in;
	unsigned int	out;
	unsigned int	mask;
	unsigned int	esize;
	void		*data;
};

#define __STRUCT_WEMQ_KFIFO_COMMON(datatype, recsize, ptrtype) \
	union { \
		struct __wemq_kfifo	wemq_kfifo; \
		datatype	*type; \
		const datatype	*const_type; \
		char		(*rectype)[recsize]; \
		ptrtype		*ptr; \
		ptrtype const	*ptr_const; \
	}

#define __STRUCT_WEMQ_KFIFO(type, size, recsize, ptrtype) \
{ \
	__STRUCT_WEMQ_KFIFO_COMMON(type, recsize, ptrtype); \
	type		buf[((size < 2) || (size & (size - 1))) ? -1 : size]; \
}

#define STRUCT_WEMQ_KFIFO(type, size) \
	struct __STRUCT_WEMQ_KFIFO(type, size, 0, type)

#define __STRUCT_WEMQ_KFIFO_PTR(type, recsize, ptrtype) \
{ \
	__STRUCT_WEMQ_KFIFO_COMMON(type, recsize, ptrtype); \
	type		buf[0]; \
}

#define STRUCT_WEMQ_KFIFO_PTR(type) \
	struct __STRUCT_WEMQ_KFIFO_PTR(type, 0, type)

/*
 * define compatibility "struct wemq_kfifo" for dynamic allocated fifos
 */
struct st_wemq_kfifo __STRUCT_WEMQ_KFIFO_PTR(unsigned char, 0, void);

#define STRUCT_WEMQ_KFIFO_REC_1(size) \
	struct __STRUCT_WEMQ_KFIFO(unsigned char, size, 1, void)

#define STRUCT_WEMQ_KFIFO_REC_2(size) \
	struct __STRUCT_WEMQ_KFIFO(unsigned char, size, 2, void)

/*
 * define wemq_kfifo_rec types
 */
struct wemq_kfifo_rec_ptr_1 __STRUCT_WEMQ_KFIFO_PTR(unsigned char, 1, void);
struct wemq_kfifo_rec_ptr_2 __STRUCT_WEMQ_KFIFO_PTR(unsigned char, 2, void);

/*
 * helper macro to distinguish between real in place fifo where the fifo
 * array is a part of the structure and the fifo type where the array is
 * outside of the fifo structure.
 */
#define	__is_wemq_kfifo_ptr(fifo)	(sizeof(*fifo) == sizeof(struct __wemq_kfifo))

/**
 * DECLARE_WEMQ_KFIFO_PTR - macro to declare a fifo pointer object
 * @fifo: name of the declared fifo
 * @type: type of the fifo elements
 */
#define DECLARE_WEMQ_KFIFO_PTR(fifo, type)	STRUCT_WEMQ_KFIFO_PTR(type) fifo

/**
 * DECLARE_WEMQ_KFIFO - macro to declare a fifo object
 * @fifo: name of the declared fifo
 * @type: type of the fifo elements
 * @size: the number of elements in the fifo, this must be a power of 2
 */
#define DECLARE_WEMQ_KFIFO(fifo, type, size)	STRUCT_WEMQ_KFIFO(type, size) fifo

/**
 * INIT_WEMQ_KFIFO - Initialize a fifo declared by DECLARE_WEMQ_KFIFO
 * @fifo: name of the declared fifo datatype
 */
#define INIT_WEMQ_KFIFO(fifo) \
(void)({ \
	typeof(&(fifo)) __tmp = &(fifo); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	__wemq_kfifo->in = 0; \
	__wemq_kfifo->out = 0; \
	__wemq_kfifo->mask = __is_wemq_kfifo_ptr(__tmp) ? 0 : ARRAY_SIZE(__tmp->buf) - 1;\
	__wemq_kfifo->esize = sizeof(*__tmp->buf); \
	__wemq_kfifo->data = __is_wemq_kfifo_ptr(__tmp) ?  NULL : __tmp->buf; \
})

/**
 * DEFINE_WEMQ_KFIFO - macro to define and initialize a fifo
 * @fifo: name of the declared fifo datatype
 * @type: type of the fifo elements
 * @size: the number of elements in the fifo, this must be a power of 2
 *
 * Note: the macro can be used for global and local fifo data type variables.
 */
#define DEFINE_WEMQ_KFIFO(fifo, type, size) \
	DECLARE_WEMQ_KFIFO(fifo, type, size) = \
	(typeof(fifo)) { \
		{ \
			{ \
			.in	= 0, \
			.out	= 0, \
			.mask	= __is_wemq_kfifo_ptr(&(fifo)) ? \
				  0 : \
				  ARRAY_SIZE((fifo).buf) - 1, \
			.esize	= sizeof(*(fifo).buf), \
			.data	= __is_wemq_kfifo_ptr(&(fifo)) ? \
				NULL : \
				(fifo).buf, \
			} \
		} \
	}


static inline unsigned int __must_check
__wemq_kfifo_uint_must_check_helper(unsigned int val)
{
	return val;
}

static inline int __must_check
__wemq_kfifo_int_must_check_helper(int val)
{
	return val;
}

/**
 * wemq_kfifo_initialized - Check if the fifo is initialized
 * @fifo: address of the fifo to check
 *
 * Return %true if fifo is initialized, otherwise %false.
 * Assumes the fifo was 0 before.
 */
#define wemq_kfifo_initialized(fifo) ((fifo)->wemq_kfifo.mask)

/**
 * wemq_kfifo_esize - returns the size of the element managed by the fifo
 * @fifo: address of the fifo to be used
 */
#define wemq_kfifo_esize(fifo)	((fifo)->wemq_kfifo.esize)

/**
 * wemq_kfifo_recsize - returns the size of the record length field
 * @fifo: address of the fifo to be used
 */
#define wemq_kfifo_recsize(fifo)	(sizeof(*(fifo)->rectype))

/**
 * wemq_kfifo_size - returns the size of the fifo in elements
 * @fifo: address of the fifo to be used
 */
#define wemq_kfifo_size(fifo)	((fifo)->wemq_kfifo.mask + 1)

/**
 * wemq_kfifo_reset - removes the entire fifo content
 * @fifo: address of the fifo to be used
 *
 * Note: usage of wemq_kfifo_reset() is dangerous. It should be only called when the
 * fifo is exclusived locked or when it is secured that no other thread is
 * accessing the fifo.
 */
#define wemq_kfifo_reset(fifo) \
(void)({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	__tmp->wemq_kfifo.in = __tmp->wemq_kfifo.out = 0; \
})

/**
 * wemq_kfifo_reset_out - skip fifo content
 * @fifo: address of the fifo to be used
 *
 * Note: The usage of wemq_kfifo_reset_out() is safe until it will be only called
 * from the reader thread and there is only one concurrent reader. Otherwise
 * it is dangerous and must be handled in the same way as wemq_kfifo_reset().
 */
#define wemq_kfifo_reset_out(fifo)	\
(void)({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	__tmp->wemq_kfifo.out = __tmp->wemq_kfifo.in; \
})

/**
 * wemq_kfifo_len - returns the number of used elements in the fifo
 * @fifo: address of the fifo to be used
 */
#define wemq_kfifo_len(fifo) \
({ \
	typeof((fifo) + 1) __tmpl = (fifo); \
	__tmpl->wemq_kfifo.in - __tmpl->wemq_kfifo.out; \
})

/**
 * wemq_kfifo_is_empty - returns true if the fifo is empty
 * @fifo: address of the fifo to be used
 */
#define	wemq_kfifo_is_empty(fifo) \
({ \
	typeof((fifo) + 1) __tmpq = (fifo); \
	__tmpq->wemq_kfifo.in == __tmpq->wemq_kfifo.out; \
})

/**
 * wemq_kfifo_is_full - returns true if the fifo is full
 * @fifo: address of the fifo to be used
 */
#define	wemq_kfifo_is_full(fifo) \
({ \
	typeof((fifo) + 1) __tmpq = (fifo); \
	wemq_kfifo_len(__tmpq) > __tmpq->wemq_kfifo.mask; \
})

/**
 * wemq_kfifo_avail - returns the number of unused elements in the fifo
 * @fifo: address of the fifo to be used
 */
#define	wemq_kfifo_avail(fifo) \
__wemq_kfifo_uint_must_check_helper( \
({ \
	typeof((fifo) + 1) __tmpq = (fifo); \
	const size_t __recsize = sizeof(*__tmpq->rectype); \
	unsigned int __avail = wemq_kfifo_size(__tmpq) - wemq_kfifo_len(__tmpq); \
	(__recsize) ? ((__avail <= __recsize) ? 0 : \
	__wemq_kfifo_max_r(__avail - __recsize, __recsize)) : \
	__avail; \
}) \
)

/**
 * wemq_kfifo_skip - skip output data
 * @fifo: address of the fifo to be used
 */
#define	wemq_kfifo_skip(fifo) \
(void)({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	const size_t __recsize = sizeof(*__tmp->rectype); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	if (__recsize) \
		__wemq_kfifo_skip_r(__wemq_kfifo, __recsize); \
	else \
		__wemq_kfifo->out++; \
})

/**
 * wemq_kfifo_peek_len - gets the size of the next fifo record
 * @fifo: address of the fifo to be used
 *
 * This function returns the size of the next fifo record in number of bytes.
 */
#define wemq_kfifo_peek_len(fifo) \
__wemq_kfifo_uint_must_check_helper( \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	const size_t __recsize = sizeof(*__tmp->rectype); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	(!__recsize) ? wemq_kfifo_len(__tmp) * sizeof(*__tmp->type) : \
	__wemq_kfifo_len_r(__wemq_kfifo, __recsize); \
}) \
)

/**
 * wemq_kfifo_alloc - dynamically allocates a new fifo buffer
 * @fifo: pointer to the fifo
 * @size: the number of elements in the fifo, this must be a power of 2
 * @gfp_mask: get_free_pages mask, passed to kmalloc()
 *
 * This macro dynamically allocates a new fifo buffer.
 *
 * The numer of elements will be rounded-up to a power of 2.
 * The fifo will be release with wemq_kfifo_free().
 * Return 0 if no error, otherwise an error code.
 */
#define wemq_kfifo_alloc(fifo, size) \
__wemq_kfifo_int_must_check_helper( \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	__is_wemq_kfifo_ptr(__tmp) ? \
	__wemq_kfifo_alloc(__wemq_kfifo, size, sizeof(*__tmp->type)) : \
	-KFIFO_EINVAL; \
}) \
)

/**
 * wemq_kfifo_free - frees the fifo
 * @fifo: the fifo to be freed
 */
#define wemq_kfifo_free(fifo) \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	if (__is_wemq_kfifo_ptr(__tmp)) \
		__wemq_kfifo_free(__wemq_kfifo); \
})

/**
 * wemq_kfifo_init - initialize a fifo using a preallocated buffer
 * @fifo: the fifo to assign the buffer
 * @buffer: the preallocated buffer to be used
 * @size: the size of the internal buffer, this have to be a power of 2
 *
 * This macro initialize a fifo using a preallocated buffer.
 *
 * The numer of elements will be rounded-up to a power of 2.
 * Return 0 if no error, otherwise an error code.
 */
#define wemq_kfifo_init(fifo, buffer, size) \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	__is_wemq_kfifo_ptr(__tmp) ? \
	__wemq_kfifo_init(__wemq_kfifo, buffer, size, sizeof(*__tmp->type)) : \
	-KFIFO_EINVAL; \
})

/**
 * wemq_kfifo_put - put data into the fifo
 * @fifo: address of the fifo to be used
 * @val: the data to be added
 *
 * This macro copies the given value into the fifo.
 * It returns 0 if the fifo was full. Otherwise it returns the number
 * processed elements.
 *
 * Note that with only one concurrent reader and one concurrent
 * writer, you don't need extra locking to use these macro.
 */
#define	wemq_kfifo_put(fifo, val) \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	typeof(*__tmp->const_type) __val = (val); \
	unsigned int __ret; \
	size_t __recsize = sizeof(*__tmp->rectype); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	if (__recsize) \
		__ret = __wemq_kfifo_in_r(__wemq_kfifo, &__val, sizeof(__val), \
			__recsize); \
	else { \
		__ret = !wemq_kfifo_is_full(__tmp); \
		if (__ret) { \
			(__is_wemq_kfifo_ptr(__tmp) ? \
			((typeof(__tmp->type))__wemq_kfifo->data) : \
			(__tmp->buf) \
			)[__wemq_kfifo->in & __tmp->wemq_kfifo.mask] = \
				*(typeof(__tmp->type))&__val; \
			__wemq_kfifo->in++; \
		} \
	} \
	__ret; \
})

/**
 * wemq_kfifo_get - get data from the fifo
 * @fifo: address of the fifo to be used
 * @val: address where to store the data
 *
 * This macro reads the data from the fifo.
 * It returns 0 if the fifo was empty. Otherwise it returns the number
 * processed elements.
 *
 * Note that with only one concurrent reader and one concurrent
 * writer, you don't need extra locking to use these macro.
 */
#define	wemq_kfifo_get(fifo, val) \
__wemq_kfifo_uint_must_check_helper( \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	typeof(__tmp->ptr) __val = (val); \
	unsigned int __ret; \
	const size_t __recsize = sizeof(*__tmp->rectype); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	if (__recsize) \
		__ret = __wemq_kfifo_out_r(__wemq_kfifo, __val, sizeof(*__val), \
			__recsize); \
	else { \
		__ret = !wemq_kfifo_is_empty(__tmp); \
		if (__ret) { \
			*(typeof(__tmp->type))__val = \
				(__is_wemq_kfifo_ptr(__tmp) ? \
				((typeof(__tmp->type))__wemq_kfifo->data) : \
				(__tmp->buf) \
				)[__wemq_kfifo->out & __tmp->wemq_kfifo.mask]; \
			__wemq_kfifo->out++; \
		} \
	} \
	__ret; \
}) \
)

/**
 * wemq_kfifo_peek - get data from the fifo without removing
 * @fifo: address of the fifo to be used
 * @val: address where to store the data
 *
 * This reads the data from the fifo without removing it from the fifo.
 * It returns 0 if the fifo was empty. Otherwise it returns the number
 * processed elements.
 *
 * Note that with only one concurrent reader and one concurrent
 * writer, you don't need extra locking to use these macro.
 */
#define	wemq_kfifo_peek(fifo, val) \
__wemq_kfifo_uint_must_check_helper( \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	typeof(__tmp->ptr) __val = (val); \
	unsigned int __ret; \
	const size_t __recsize = sizeof(*__tmp->rectype); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	if (__recsize) \
		__ret = __wemq_kfifo_out_peek_r(__wemq_kfifo, __val, sizeof(*__val), \
			__recsize); \
	else { \
		__ret = !wemq_kfifo_is_empty(__tmp); \
		if (__ret) { \
			*(typeof(__tmp->type))__val = \
				(__is_wemq_kfifo_ptr(__tmp) ? \
				((typeof(__tmp->type))__wemq_kfifo->data) : \
				(__tmp->buf) \
				)[__wemq_kfifo->out & __tmp->wemq_kfifo.mask]; \
		} \
	} \
	__ret; \
}) \
)

/**
 * wemq_kfifo_in - put data into the fifo
 * @fifo: address of the fifo to be used
 * @buf: the data to be added
 * @n: number of elements to be added
 *
 * This macro copies the given buffer into the fifo and returns the
 * number of copied elements.
 *
 * Note that with only one concurrent reader and one concurrent
 * writer, you don't need extra locking to use these macro.
 */
#define	wemq_kfifo_in(fifo, buf, n) \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	typeof(__tmp->ptr_const) __buf = (buf); \
	unsigned long __n = (n); \
	const size_t __recsize = sizeof(*__tmp->rectype); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	(__recsize) ?\
	__wemq_kfifo_in_r(__wemq_kfifo, __buf, __n, __recsize) : \
	__wemq_kfifo_in(__wemq_kfifo, __buf, __n); \
})


/**
 * wemq_kfifo_out - get data from the fifo
 * @fifo: address of the fifo to be used
 * @buf: pointer to the storage buffer
 * @n: max. number of elements to get
 *
 * This macro get some data from the fifo and return the numbers of elements
 * copied.
 *
 * Note that with only one concurrent reader and one concurrent
 * writer, you don't need extra locking to use these macro.
 */
#define	wemq_kfifo_out(fifo, buf, n) \
__wemq_kfifo_uint_must_check_helper( \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	typeof(__tmp->ptr) __buf = (buf); \
	unsigned long __n = (n); \
	const size_t __recsize = sizeof(*__tmp->rectype); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	(__recsize) ?\
	__wemq_kfifo_out_r(__wemq_kfifo, __buf, __n, __recsize) : \
	__wemq_kfifo_out(__wemq_kfifo, __buf, __n); \
}) \
)

/**
 * wemq_kfifo_out_peek - gets some data from the fifo
 * @fifo: address of the fifo to be used
 * @buf: pointer to the storage buffer
 * @n: max. number of elements to get
 *
 * This macro get the data from the fifo and return the numbers of elements
 * copied. The data is not removed from the fifo.
 *
 * Note that with only one concurrent reader and one concurrent
 * writer, you don't need extra locking to use these macro.
 */
#define	wemq_kfifo_out_peek(fifo, buf, n) \
__wemq_kfifo_uint_must_check_helper( \
({ \
	typeof((fifo) + 1) __tmp = (fifo); \
	typeof(__tmp->ptr) __buf = (buf); \
	unsigned long __n = (n); \
	const size_t __recsize = sizeof(*__tmp->rectype); \
	struct __wemq_kfifo *__wemq_kfifo = &__tmp->wemq_kfifo; \
	(__recsize) ? \
	__wemq_kfifo_out_peek_r(__wemq_kfifo, __buf, __n, __recsize) : \
	__wemq_kfifo_out_peek(__wemq_kfifo, __buf, __n); \
}) \
)

extern int __wemq_kfifo_alloc(struct __wemq_kfifo *fifo, unsigned int size,
	size_t esize);

extern void __wemq_kfifo_free(struct __wemq_kfifo *fifo);

extern int __wemq_kfifo_init(struct __wemq_kfifo *fifo, void *buffer,
	unsigned int size, size_t esize);

extern unsigned int __wemq_kfifo_in(struct __wemq_kfifo *fifo,
	const void *buf, unsigned int len);

extern unsigned int __wemq_kfifo_out(struct __wemq_kfifo *fifo,
	void *buf, unsigned int len);

extern unsigned int __wemq_kfifo_out_peek(struct __wemq_kfifo *fifo,
	void *buf, unsigned int len);

extern unsigned int __wemq_kfifo_in_r(struct __wemq_kfifo *fifo,
	const void *buf, unsigned int len, size_t recsize);

extern unsigned int __wemq_kfifo_out_r(struct __wemq_kfifo *fifo,
	void *buf, unsigned int len, size_t recsize);

extern unsigned int __wemq_kfifo_len_r(struct __wemq_kfifo *fifo, size_t recsize);

extern void __wemq_kfifo_skip_r(struct __wemq_kfifo *fifo, size_t recsize);

extern unsigned int __wemq_kfifo_out_peek_r(struct __wemq_kfifo *fifo,
	void *buf, unsigned int len, size_t recsize);

extern unsigned int __wemq_kfifo_max_r(unsigned int len, size_t recsize);
#ifdef __cplusplus
}
#endif
#endif

