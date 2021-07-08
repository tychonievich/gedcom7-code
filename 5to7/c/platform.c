#include "platform.h"

#ifdef _MSC_VER
char *strndup( const char *str, size_t size )
{
    char* buf = malloc(size + 1);
    if (buf != 0) {
        strncpy(buf, str, size);
        buf[size] = 0;
    }
    return buf;
}
#endif
