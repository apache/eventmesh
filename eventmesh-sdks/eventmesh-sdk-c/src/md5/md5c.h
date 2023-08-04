#ifndef md5c__H
#define md5c__H

#include <sys/types.h>
#include <inttypes.h>

#include <string.h>

#ifdef __cplusplus
extern "C"
{
#endif

//#pragma pack(push) //压栈保存
//#pragma pack(1)// 设置1字节对齐
#ifdef _WIN32
#pragma pack( push, 1)
#else
#pragma pack(1)
#endif
typedef struct {
  uint32_t state[4];        /* state (ABCD) */
  uint32_t count[2];        /* number of bits, modulo 2^64 (lsb first) */
  u_char buffer[64];         /* input buffer */
} MD5_CTX;

#ifdef _WIN32
#pragma pack(pop)
#else
#pragma pack()
#endif
//#pragma pack(pop) // 恢复先前设置


void MD5Init(MD5_CTX *);
void MD5Update(MD5_CTX *, unsigned char *, unsigned int);
void MD5Final(u_char [16], MD5_CTX *);

//char* MD5Output(char md5[16]);
char* md5_get_str(char md5[16]);

char* md5_str(u_char* buf,int size);
char* md5_buf(u_char* buf,int size);

char* md5_file(char* filename);


//可以把中间结果dump 出来， 然后下次load 进去，继续计算， delexxie 2009.03.13
//md5_ctx len = 88, 确保buf 长度大于 88 字节
void md5_dump_ctx(MD5_CTX *md5_ctx, char* buf, unsigned int *dump_len);
void md5_load_ctx(MD5_CTX *md5_ctx, char* buf);


#ifdef __cplusplus
}
#endif

#endif
