#!/bin/bash

make --quiet
find tests/5 -type f -name '*.ged' | sort -V | while read v5
do
    v7=tests/7${v5#*5}
    cmp=$([ "$#" -gt 0 ] && echo -n "diff" || echo -n "cmp -s")
    ./ged5to7 $v5 2>/dev/null | $cmp $v7 - && echo "    OK" ${v5#*5/} || echo FAILED ${v5#*5/}
done
