#include <stdint.h>
#include <stdlib.h>
#include <time.h>
#include <stdbit.h>
#include <immintrin.h>

typedef uint64_t u64;

typedef struct {
    u64 data[4096 / 32];
} blocks_t;

typedef struct {
    u64 data[4096 / 64];
} blocks_t_1bit;

typedef struct {
    u64 data[4];
} counts_t;

void slowCount2bit(counts_t* counts,u64 ix) {
    for (int i = 0;i < 32;i++) {
        counts->data[(ix >> (2 * i)) & 0x3]++;
    }
}

void fastCount2bit(counts_t* counts,u64 ix) {
    counts->data[0] += stdc_count_ones_ul(~ix >> 1 & 0x5555555555555555l & ~ix);
    counts->data[1] += stdc_count_ones_ul(~ix >> 1 & 0x5555555555555555l & ix);
    counts->data[2] += stdc_count_ones_ul(ix >> 1 & 0x5555555555555555l & ~ix);
    counts->data[3] += stdc_count_ones_ul(ix >> 1 & 0x5555555555555555l & ix);
}

void fastCount3bit(counts_t* counts,u64 x) {
    u64 m = 0x9249249249249249l;
    counts->data[0] += stdc_count_ones_ul((~x >> 2) & (~x >> 1) & ~x & m);
    counts->data[1] += stdc_count_ones_ul((~x >> 2) & (~x >> 1) &  x & m);
    counts->data[2] += stdc_count_ones_ul((~x >> 2) & ( x >> 1) & ~x & m);
    counts->data[3] += stdc_count_ones_ul((~x >> 2) & ( x >> 1) &  x & m);
    counts->data[4] += stdc_count_ones_ul(( x >> 2) & (~x >> 1) & ~x & m);
    counts->data[5] += stdc_count_ones_ul(( x >> 2) & (~x >> 1) &  x & m);
    counts->data[6] += stdc_count_ones_ul(( x >> 2) & ( x >> 1) & ~x & m);
    counts->data[7] += stdc_count_ones_ul(( x >> 2) & ( x >> 1) &  x & m);
}

void fastResize1to2bit(blocks_t* big,blocks_t_1bit* small) {
    for (int i = 0;i < 64;i++) {
        big->data[(i << 1) | 0] = _pdep_u64(small->data[i], 0x5555555555555555l);
        big->data[(i << 1) | 1] = _pdep_u64(small->data[i] >> 32, 0x5555555555555555l);
    }
}

int main() {
    srand(time(NULL));
    return 0;
}