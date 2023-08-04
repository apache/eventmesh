#include <fcntl.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <arpa/inet.h>

#include "md5c.h"

#define S11 7
#define S12 12
#define S13 17
#define S14 22
#define S21 5
#define S22 9
#define S23 14
#define S24 20
#define S31 4
#define S32 11
#define S33 16
#define S34 23
#define S41 6
#define S42 10
#define S43 15
#define S44 21

static void MD5Transform (uint32_t[4], unsigned char[64]);
static void Encode (unsigned char *, uint32_t *, unsigned int);
static void Decode (uint32_t *, unsigned char *, unsigned int);
static void MD5_memcpy (u_char *, u_char *, unsigned int);
static void MD5_memset (u_char *, int, unsigned int);

static unsigned char PADDING[64] = {
  0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
};

/* F, G, H and I are basic MD5 functions.
 */
#define F(x, y, z) (((x) & (y)) | ((~x) & (z)))
#define G(x, y, z) (((x) & (z)) | ((y) & (~z)))
#define H(x, y, z) ((x) ^ (y) ^ (z))
#define I(x, y, z) ((y) ^ ((x) | (~z)))

/* ROTATE_LEFT rotates x left n bits.
 */
#define ROTATE_LEFT(x, n) (((x) << (n)) | ((x) >> (32-(n))))

/* FF, GG, HH, and II transformations for rounds 1, 2, 3, and 4.
Rotation is separate from addition to prevent recomputation.
 */
#define FF(a, b, c, d, x, s, ac) { \
 (a) += F ((b), (c), (d)) + (x) + (uint32_t)(ac); \
 (a) = ROTATE_LEFT ((a), (s)); \
 (a) += (b); \
  }
#define GG(a, b, c, d, x, s, ac) { \
 (a) += G ((b), (c), (d)) + (x) + (uint32_t)(ac); \
 (a) = ROTATE_LEFT ((a), (s)); \
 (a) += (b); \
  }
#define HH(a, b, c, d, x, s, ac) { \
 (a) += H ((b), (c), (d)) + (x) + (uint32_t)(ac); \
 (a) = ROTATE_LEFT ((a), (s)); \
 (a) += (b); \
  }
#define II(a, b, c, d, x, s, ac) { \
 (a) += I ((b), (c), (d)) + (x) + (uint32_t)(ac); \
 (a) = ROTATE_LEFT ((a), (s)); \
 (a) += (b); \
  }

/* MD5 initialization. Begins an MD5 operation, writing a new context.
 */
void
MD5Init (MD5_CTX* context)
    // MD5_CTX *context;		/* context */
{
  context->count[0] = context->count[1] = 0;
  /* Load magic initialization constants.
   */
  context->state[0] = 0x67452301;
  context->state[1] = 0xefcdab89;
  context->state[2] = 0x98badcfe;
  context->state[3] = 0x10325476;
}

/* MD5 block update operation. Continues an MD5 message-digest
  operation, processing another message block, and updating the
  context.
 */
void
MD5Update (MD5_CTX *context, unsigned char *input, unsigned int inputLen)
//     MD5_CTX *context;		/* context */
//     unsigned char *input;	/* input block */
//     unsigned int inputLen;	/* length of input block */
{
  unsigned int i, index, partLen;

  /* Compute number of bytes mod 64 */
  index = (unsigned int) ((context->count[0] >> 3) & 0x3F);

  /* Update number of bits */
  if ((context->count[0] += ((uint32_t) inputLen << 3))
      < ((uint32_t) inputLen << 3))
    context->count[1]++;
  context->count[1] += ((uint32_t) inputLen >> 29);

  partLen = 64 - index;

  /* Transform as many times as possible.
   */
  if (inputLen >= partLen)
    {
      MD5_memcpy
	((u_char *) & context->buffer[index], (u_char *) input, partLen);
      MD5Transform (context->state, context->buffer);

      for (i = partLen; i + 63 < inputLen; i += 64)
	MD5Transform (context->state, &input[i]);

      index = 0;
    }
  else
    i = 0;

  /* Buffer remaining input */
  MD5_memcpy
    ((u_char *) & context->buffer[index], (u_char *) & input[i],
     inputLen - i);
}

/* MD5 finalization. Ends an MD5 message-digest operation, writing the
  the message digest and zeroizing the context.
 */
void
MD5Final (unsigned char digest[16], MD5_CTX *context)
//     unsigned char digest[16];	/* message digest */
//     MD5_CTX *context;		/* context */
{
  unsigned char bits[8];
  unsigned int index, padLen;

  /* Save number of bits */
  Encode (bits, context->count, 8);

  /* Pad out to 56 mod 64.
   */
  index = (unsigned int) ((context->count[0] >> 3) & 0x3f);
  padLen = (index < 56) ? (56 - index) : (120 - index);
  MD5Update (context, PADDING, padLen);

  /* Append length (before padding) */
  MD5Update (context, bits, 8);
  /* Store state in digest */
  Encode (digest, context->state, 16);

  /* Zeroize sensitive information.
   */
  MD5_memset ((u_char *) context, 0, sizeof (*context));
}

/* MD5 basic transformation. Transforms state based on block.
 */
static void
MD5Transform (uint32_t state[4], unsigned char block[64])
//     uint32_t state[4];
//     unsigned char block[64];
{
  uint32_t a = state[0], b = state[1], c = state[2], d = state[3], x[16];

  Decode (x, block, 64);

  /* Round 1 */
  FF (a, b, c, d, x[0], S11, 0xd76aa478);	/* 1 */
  FF (d, a, b, c, x[1], S12, 0xe8c7b756);	/* 2 */
  FF (c, d, a, b, x[2], S13, 0x242070db);	/* 3 */
  FF (b, c, d, a, x[3], S14, 0xc1bdceee);	/* 4 */
  FF (a, b, c, d, x[4], S11, 0xf57c0faf);	/* 5 */
  FF (d, a, b, c, x[5], S12, 0x4787c62a);	/* 6 */
  FF (c, d, a, b, x[6], S13, 0xa8304613);	/* 7 */
  FF (b, c, d, a, x[7], S14, 0xfd469501);	/* 8 */
  FF (a, b, c, d, x[8], S11, 0x698098d8);	/* 9 */
  FF (d, a, b, c, x[9], S12, 0x8b44f7af);	/* 10 */
  FF (c, d, a, b, x[10], S13, 0xffff5bb1);	/* 11 */
  FF (b, c, d, a, x[11], S14, 0x895cd7be);	/* 12 */
  FF (a, b, c, d, x[12], S11, 0x6b901122);	/* 13 */
  FF (d, a, b, c, x[13], S12, 0xfd987193);	/* 14 */
  FF (c, d, a, b, x[14], S13, 0xa679438e);	/* 15 */
  FF (b, c, d, a, x[15], S14, 0x49b40821);	/* 16 */

  /* Round 2 */
  GG (a, b, c, d, x[1], S21, 0xf61e2562);	/* 17 */
  GG (d, a, b, c, x[6], S22, 0xc040b340);	/* 18 */
  GG (c, d, a, b, x[11], S23, 0x265e5a51);	/* 19 */
  GG (b, c, d, a, x[0], S24, 0xe9b6c7aa);	/* 20 */
  GG (a, b, c, d, x[5], S21, 0xd62f105d);	/* 21 */
  GG (d, a, b, c, x[10], S22, 0x2441453);	/* 22 */
  GG (c, d, a, b, x[15], S23, 0xd8a1e681);	/* 23 */
  GG (b, c, d, a, x[4], S24, 0xe7d3fbc8);	/* 24 */
  GG (a, b, c, d, x[9], S21, 0x21e1cde6);	/* 25 */
  GG (d, a, b, c, x[14], S22, 0xc33707d6);	/* 26 */
  GG (c, d, a, b, x[3], S23, 0xf4d50d87);	/* 27 */
  GG (b, c, d, a, x[8], S24, 0x455a14ed);	/* 28 */
  GG (a, b, c, d, x[13], S21, 0xa9e3e905);	/* 29 */
  GG (d, a, b, c, x[2], S22, 0xfcefa3f8);	/* 30 */
  GG (c, d, a, b, x[7], S23, 0x676f02d9);	/* 31 */
  GG (b, c, d, a, x[12], S24, 0x8d2a4c8a);	/* 32 */

  /* Round 3 */
  HH (a, b, c, d, x[5], S31, 0xfffa3942);	/* 33 */
  HH (d, a, b, c, x[8], S32, 0x8771f681);	/* 34 */
  HH (c, d, a, b, x[11], S33, 0x6d9d6122);	/* 35 */
  HH (b, c, d, a, x[14], S34, 0xfde5380c);	/* 36 */
  HH (a, b, c, d, x[1], S31, 0xa4beea44);	/* 37 */
  HH (d, a, b, c, x[4], S32, 0x4bdecfa9);	/* 38 */
  HH (c, d, a, b, x[7], S33, 0xf6bb4b60);	/* 39 */
  HH (b, c, d, a, x[10], S34, 0xbebfbc70);	/* 40 */
  HH (a, b, c, d, x[13], S31, 0x289b7ec6);	/* 41 */
  HH (d, a, b, c, x[0], S32, 0xeaa127fa);	/* 42 */
  HH (c, d, a, b, x[3], S33, 0xd4ef3085);	/* 43 */
  HH (b, c, d, a, x[6], S34, 0x4881d05);	/* 44 */
  HH (a, b, c, d, x[9], S31, 0xd9d4d039);	/* 45 */
  HH (d, a, b, c, x[12], S32, 0xe6db99e5);	/* 46 */
  HH (c, d, a, b, x[15], S33, 0x1fa27cf8);	/* 47 */
  HH (b, c, d, a, x[2], S34, 0xc4ac5665);	/* 48 */

  /* Round 4 */
  II (a, b, c, d, x[0], S41, 0xf4292244);	/* 49 */
  II (d, a, b, c, x[7], S42, 0x432aff97);	/* 50 */
  II (c, d, a, b, x[14], S43, 0xab9423a7);	/* 51 */
  II (b, c, d, a, x[5], S44, 0xfc93a039);	/* 52 */
  II (a, b, c, d, x[12], S41, 0x655b59c3);	/* 53 */
  II (d, a, b, c, x[3], S42, 0x8f0ccc92);	/* 54 */
  II (c, d, a, b, x[10], S43, 0xffeff47d);	/* 55 */
  II (b, c, d, a, x[1], S44, 0x85845dd1);	/* 56 */
  II (a, b, c, d, x[8], S41, 0x6fa87e4f);	/* 57 */
  II (d, a, b, c, x[15], S42, 0xfe2ce6e0);	/* 58 */
  II (c, d, a, b, x[6], S43, 0xa3014314);	/* 59 */
  II (b, c, d, a, x[13], S44, 0x4e0811a1);	/* 60 */
  II (a, b, c, d, x[4], S41, 0xf7537e82);	/* 61 */
  II (d, a, b, c, x[11], S42, 0xbd3af235);	/* 62 */
  II (c, d, a, b, x[2], S43, 0x2ad7d2bb);	/* 63 */
  II (b, c, d, a, x[9], S44, 0xeb86d391);	/* 64 */

  state[0] += a;
  state[1] += b;
  state[2] += c;
  state[3] += d;

  /* Zeroize sensitive information.
   */
  MD5_memset ((u_char *) x, 0, sizeof (x));
}

/* Encodes input (uint32_t) into output (unsigned char). Assumes len is
  a multiple of 4.
 */
static void
Encode(unsigned char *output,
     uint32_t *input,
     unsigned int len)
{
  unsigned int i, j;

  for (i = 0, j = 0; j < len; i++, j += 4)
    {
      output[j] = (unsigned char) (input[i] & 0xff);
      output[j + 1] = (unsigned char) ((input[i] >> 8) & 0xff);
      output[j + 2] = (unsigned char) ((input[i] >> 16) & 0xff);
      output[j + 3] = (unsigned char) ((input[i] >> 24) & 0xff);
    }
}

/* Decodes input (unsigned char) into output (uint32_t). Assumes len is
  a multiple of 4.
 */
static void
Decode (uint32_t *output,
     unsigned char *input,
     unsigned int len)
{
  unsigned int i, j;

  for (i = 0, j = 0; j < len; i++, j += 4)
    output[i] = ((uint32_t) input[j]) | (((uint32_t) input[j + 1]) << 8) |
      (((uint32_t) input[j + 2]) << 16) | (((uint32_t) input[j + 3]) << 24);
}

/* Note: Replace "for loop" with standard memcpy if possible.
 */

static void
MD5_memcpy(u_char *output,
     u_char *input,
     unsigned int len)
{
  unsigned int i;

  for (i = 0; i < len; i++)
    output[i] = input[i];
}

/* Note: Replace "for loop" with standard memset if possible.
 */
static void
MD5_memset (u_char *output,
     int value,
     unsigned int len)
{
  unsigned int i;

  for (i = 0; i < len; i++)
    ((char *) output)[i] = (char) value;
}

//char* MD5Output(char md5[16])
//{
//    char tmp[100];
//    memset(tmp, 0, 100);
//    snprintf(tmp, 99, "%u %u %u %u",
//        *(unsigned *)(md5),
//        *(unsigned *)(md5+4),
//        *(unsigned *)(md5+8),
//        *(unsigned *)(md5+12)
//    );
//
//    return std::string(tmp);
//}

char* md5_get_str(char md5[16])
{
    static u_char p[33];
    int hexlen = 16;
    unsigned char c, s;
    int i = 0;

    while(hexlen > 0)
    {
        c = *md5++;
        hexlen--;

        s = 0x0F & c;
        if ( s < 10 )
        {
            p[i + 1] = '0' + s;
        }
        else
        {
            p[i + 1] = 'a' + (s - 10);
        }

        c >>= 4;
        s = 0x0F & c;
        if ( s < 10 )
        {
            p[i] = '0' + s;
        }
        else
        {
            p[i] = 'a' + (s - 10);
        }
        i += 2;
    }
    p[i++] = '\0';

    return (char*)p;
}

char* md5_buf(u_char* buf,int size)
{
	static u_char p[16];
    MD5_CTX md5;
    MD5Init(&md5);
    MD5Update(&md5,(u_char*)buf,size);
    MD5Final(p,&md5);
	return (char*)p;
}

char* md5_str(u_char* buf,int size)
{
	int i;
	u_char *p;
	u_char *q;
	static char tmp[36];
	p = (u_char*)md5_buf(buf,size);
	q = (u_char*)tmp;
	for(i = 0 ; i < 16 ; i++,q+=2)
		snprintf((char*)q, 35 - (q - (u_char*)tmp), "%02x",p[i]);
	return tmp;
}

const int BUFSIZE = 1024*1024*10;
char* md5_file(char* filename)
{
	int fd,rd,i;
	u_char p[16];
	char *q;
	static char tmp[36];
	char buf[BUFSIZE];
	struct stat st;
    MD5_CTX md5;

	
	if( ( fd = open(filename,O_RDONLY)) == -1)
	{
		return NULL;
	}
	fstat(fd,&st);
	/** Ŀ¼ */
	if(S_ISDIR(st.st_mode))
	{
		return NULL;
	}

    MD5Init(&md5);
	memset(tmp,0,36);
	memset(buf,0,BUFSIZE);

	for(;;)
	{
		rd = read(fd,buf,BUFSIZE);
    	MD5Update(&md5,(u_char*)buf,rd);
		if(rd < BUFSIZE)
			break;
	}
	close(fd);
    MD5Final(p,&md5);
	q = tmp;
	for(i = 0 ; i < 16 ; i++,q+=2)
		snprintf(q, 35 - (q - tmp), "%02x",p[i]);
	return tmp;
}

//md5_ctx len = 88
void md5_dump_ctx(MD5_CTX *md5_ctx, char* buf, unsigned int *dump_len)
{
	uint32_t len = 0;
	uint32_t k;

	k = htonl(md5_ctx->state[0]); memcpy(buf + len, &k, 4); 	len += 4;
	k = htonl(md5_ctx->state[1]); memcpy(buf + len, &k, 4); 	len += 4;
	k = htonl(md5_ctx->state[2]); memcpy(buf + len, &k, 4); 	len += 4;
	k = htonl(md5_ctx->state[3]); memcpy(buf + len, &k, 4); 	len += 4;

	k = htonl(md5_ctx->count[0]); memcpy(buf + len, &k, 4); 	len += 4;
	k = htonl(md5_ctx->count[1]); memcpy(buf + len, &k, 4); 	len += 4;
	memcpy(buf + len, md5_ctx->buffer, 64);	len += 64;
  
	dump_len = len;
}

void md5_load_ctx(MD5_CTX *md5_ctx, char* buf)
{
	uint32_t len = 0;
	memcpy(md5_ctx->state, buf + len, 16); 	len += 16;
	memcpy(md5_ctx->count, buf + len, 8); 	len += 8;
	memcpy(md5_ctx->buffer, buf + len, 64);

	md5_ctx->state[0] = ntohl(md5_ctx->state[0]);
	md5_ctx->state[1] = ntohl(md5_ctx->state[1]);
	md5_ctx->state[2] = ntohl(md5_ctx->state[2]);
	md5_ctx->state[3] = ntohl(md5_ctx->state[3]);

	md5_ctx->count[0] = ntohl(md5_ctx->count[0]);
	md5_ctx->count[1] = ntohl(md5_ctx->count[1]);
}

