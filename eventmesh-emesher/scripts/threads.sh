#!/bin/bash
jstack $(jcmd | grep ProxyStartup | cut -d" " -f1) | \
    grep -vE "(Concurrent GC|DestroyJavaVM|Gang worker|Compiler|Attach Listener|GC Thread|VM Thread|Finalizer)" | \
    grep -oE '^".*?"' | \
    sed -r  's/"(.+)(-|_)([0-9]+).*"/\1/g' | \
    sort | uniq -c | sort -nk1
