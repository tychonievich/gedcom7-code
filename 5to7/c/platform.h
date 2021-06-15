#pragma once

#ifdef _MSC_VER
#define strncasecmp _strnicmp
#define strcasecmp _stricmp

char *strndup(const char *str, size_t size);
#endif
