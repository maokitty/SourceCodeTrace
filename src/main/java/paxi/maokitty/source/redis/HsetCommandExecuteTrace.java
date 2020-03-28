package paxi.maokitty.source.redis;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.KeyPoint;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/7/21.
 */
@Background(
        target = "了解用户执行hset之后，redis底层的支持hash的方式",
        conclusion = "hash底部使用dict的结构存储,每个dict会自带当前的数据类型对应hash计算函数等，以及是否正在进行rehash，为了实现Rehash，它自己会有两个hash表的引用" +
                "每个hash表都存一个entry的数组，当遇到冲突的时候，就使用链表的方式来解决",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"

)
public class HsetCommandExecuteTrace {
    @Main
    @Trace(
            index = 0,
            originClassName = "t_hash.c",
            function = "void hsetCommand(client *c)",
            introduction = "从server中的映射可以看到redis执行具体的hset函数的方法为 hsetCommand"
    )
    public void hsetCommand(){
        //...
        Code.SLICE.source("if ((o = hashTypeLookupWriteOrCreate(c,c->argv[1])) == NULL) return;")
                .interpretation("根据命令提供的key，去查是否有对应的key存在，没有就新建一个key对象,有则只有它是一个 hash 类型才获取对象，否则报错");
        Code.SLICE.source("hashTypeTryConversion(o,c->argv,2,c->argc-1);")
                .interpretation("判断参数的长度，如果超过了 hash_max_ziplist_value 就更改为 OBJ_ENCODING_HT");
        Code.SLICE.source("for (i = 2; i < c->argc; i += 2)" +
                        "        created += !hashTypeSet(o,c->argv[i]->ptr,c->argv[i+1]->ptr,HASH_SET_COPY);")
                .interpretation("对每一个键值对分别处理");
        //...
    }
    @Trace(
            index = 1,
            originClassName = "t_hash.c",
            function = "robj *hashTypeLookupWriteOrCreate(client *c, robj *key) "
    )
    public void hashTypeLookupWriteOrCreate(){
        Code.SLICE.source("robj *o = lookupKeyWrite(c->db,key);")
                .interpretation("根据提供的dict本身的key，注意这里不是dict中元素的key,而是查找dict的key,比如 user:100 age 12 这里的key是 user:100");
        Code.SLICE.source("if (o == NULL) {" +
                "        o = createHashObject();" +
                "        dbAdd(c->db,key,o);" +
                "    } else {" +
                "        if (o->type != OBJ_HASH) {" +
                "            addReply(c,shared.wrongtypeerr);" +
                "            return NULL;" +
                "        }" +
                "    }")
                .interpretation("如果存在就仅校验是否是hash，满足条件返回；如果不存在就创建一个hash对象，并把这个key的关系存到了自己的db中");
    }

    @Trace(
            index = 2,
            originClassName = "db.c",
            function = "robj *lookupKey(redisDb *db, robj *key, int flags) "
    )
    public void lookupKey(){
        Code.SLICE.source("dictEntry *de = dictFind(db->dict,key->ptr);")
                .interpretation("首先去查找key是不是存在，可以看出，它其实就是一个dict,如果找到了更新访问的时间戳，并返回，否则没有找到");
        //...
    }

    @KeyPoint
    @Trace(
            index = 3,
            originClassName = "object.c",
            function = "robj *createHashObject(void) "
    )
    public void createHashObject(){
        Code.SLICE.source("unsigned char *zl = ziplistNew();" +
                "    robj *o = createObject(OBJ_HASH, zl);" +
                "    o->encoding = OBJ_ENCODING_ZIPLIST;" +
                "    return o;")
                .interpretation("默认创建的hash结构，它的编码方式使用的是ziplist");
    }
    @Trace(
            index = 4,
            originClassName = "db.c",
            function = "void dbAdd(redisDb *db, robj *key, robj *val) "
    )
    public void  dbAdd(){
        //...
        Code.SLICE.source("int retval = dictAdd(db->dict, copy, val);")
                .interpretation("copy就是key值的一份副本，val就是dict,表示这个key对应的dict结构已经存储在了redis的db中");
        //...
    }

    @KeyPoint
    @Trace(
            index = 5,
            originClassName = "t_hash.c",
            function = "void hashTypeTryConversion(robj *o, robj **argv, int start, int end) "
    )
    public void hashTypeTryConversion(){
        //...
        Code.SLICE.source("for (i = start; i <= end; i++) {" +
                "        if (sdsEncodedObject(argv[i]) &&" +
                "            sdslen(argv[i]->ptr) > server.hash_max_ziplist_value)" +
                "        {" +
                "            hashTypeConvert(o, OBJ_ENCODING_HT);" +
                "            break;" +
                "        }" +
                "    }")
                .interpretation("一个个的获取string类型的参数的长度，如果它超过了 定义的 hash_max_ziplist_value 即 64字节，那么类型就给为 Hashtable");
    }
    @Trace(
            index = 6,
            originClassName = "t_hash.c",
            function = "int hashTypeSet(robj *o, sds field, sds value, int flags) ",
            introduction = "添加一个新的字段，如果存在一样的就覆盖，如果是 insert就返回0，update返回1"
    )
    public void hashTypeSet(){
        //...
        Code.SLICE.source("if (o->encoding == OBJ_ENCODING_ZIPLIST){" +
                "..." +
                " if (hashTypeLength(o) > server.hash_max_ziplist_entries)" +
                "            hashTypeConvert(o, OBJ_ENCODING_HT);" +
                "}")
                .interpretation("根据编码方式来做不同的set,如果是 ZIPLIST,插入完成之后，会统计当前存储的个数，如果超过了 hash_max_ziplist_entries （512) 那么转换为  OBJ_ENCODING_HT ");
        Code.SLICE.source("} else if (o->encoding == OBJ_ENCODING_HT) {")
                .interpretation("处理 HashTable的编码方式");
        Code.SLICE.source("         dictEntry *de = dictFind(o->ptr,field);")
                .interpretation("在当前key对应的dict中去查找，有没有这个字段对应的值");
        Code.SLICE.source("         if (de) {" +
                            "            sdsfree(dictGetVal(de));" +
                            "            if (flags & HASH_SET_TAKE_VALUE) {" +
                            "                dictGetVal(de) = value;" +
                            "                value = NULL;" +
                            "            } else {" +
                            "                dictGetVal(de) = sdsdup(value);" +
                            "            }" +
                            "            update = 1;" +
                            "        }")
                .interpretation("如果存在释放原来的dict中值的空间，插入新的值，并标识是更新");
        //...
        Code.SLICE.source("dictAdd(o->ptr,f,v);")
                .interpretation("将key和value加入到dict中");
        //...
    }

    @Trace(
            index = 7,
            originClassName = "dict.h",
            function = "typedef struct dict  and typedef struct dictEntry  and  typedef struct dictType and typedef struct dictht"
    )
    public void typedefOfDictAndDictEntry(){
        Code.SLICE.source("typedef struct dictType {" +
                "    uint64_t (*hashFunction)(const void *key);" +
                "    void *(*keyDup)(void *privdata, const void *key);" +
                "    void *(*valDup)(void *privdata, const void *obj);" +
                "    int (*keyCompare)(void *privdata, const void *key1, const void *key2);" +
                "    void (*keyDestructor)(void *privdata, void *key);" +
                "    void (*valDestructor)(void *privdata, void *obj);" +
                "} dictType;")
                .interpretation("dict的操作函数");

        Code.SLICE.source("typedef struct dictEntry {" +
                "    void *key;" +
                "    union {" +
                "        void *val;" +
                "        uint64_t u64;" +
                "        int64_t s64;" +
                "        double d;" +
                "    } v;" +
                "    struct dictEntry *next;" +
                "} dictEntry;")
                .interpretation("用来表示在hashtable中存储的每一项")
                .interpretation("key是一个函数指针，v使用 union,在对应的值上会选择对应的方式来存储，以便节省空间，同时包含一个指向下个dictEntry的指针");

        Code.SLICE.source("typedef struct dictht {" +
                "    dictEntry **table;" +
                "    unsigned long size;" +
                "    unsigned long sizemask;" +
                "    unsigned long used;" +
                "} dictht;")
                .interpretation("hash表的结构")
                .interpretation("table字段是最终存储数据的地方")
                .interpretation("size表示当前hash表分配的大小,它总是2的幂次方，如果超过LONG_MAX，就会固定为 LONG_MAX+1LU")
                .interpretation("sizemask比size小1，用来将hash值映射到下标")
                .interpretation("used表示当前存了多少的元素");

        Code.SLICE.source("typedef struct dict {" +
                "    dictType *type;" +
                "    void *privdata;" +
                "    dictht ht[2];" +
                "    long rehashidx; /* rehashing not in progress if rehashidx == -1 */" +
                "    unsigned long iterators; /* number of iterators currently running */" +
                "} dict;")
                .interpretation("字典结构")
                .interpretation("dictType使得redis可以对任意类型的key和value对应类型来操作")
                .interpretation("privdata存储用户传进来的值，key就是key,value就是value")
                .interpretation("dictht数组存储两个ht,在rehash的时候，ht[0]表示旧的，ht[1]表示新的，当rehash完成，再将ht[1]地址给ht[0]")
                .interpretation("rehashidx用来标识是否正在进行rehash,没有进行的时候是-1")
                .interpretation("iterators表示当前正在进行遍历的iterator的个数,如果要进行rehash，但是当前有迭代器正在进行遍历，不会进行rehash");
    }


    @Trace(
            index = 8,
            originClassName = "dict.c",
            function = "dictEntry *dictFind(dict *d, const void *key)"
    )
    public void  dictFind(){
        //...
        Code.SLICE.source("if (dictIsRehashing(d)) _dictRehashStep(d);")
                .interpretation("如果rehash还没有结束，并且能进行rehash则先执行1步Rehash(dict的rehash是每次只rehash一部分)");
        Code.SLICE.source("h = dictHashKey(d, key);")
                .interpretation("根据key的hash算法计算出hash值");
        Code.SLICE.source("for (table = 0; table <= 1; table++) ")
                .interpretation("dict有两个hashtable,如果正在rehash,那么一个是旧的数据，一个是新的数据");
        Code.SLICE.interpretation("idx = h & d->ht[table].sizemask;")
                .interpretation("根据hash值与掩码(掩码是2的幂次方减1，所以它一定是全1的数字)，相当于对它求余,得到的结果就是在hash表中的下标");
        Code.SLICE.source("        he = d->ht[table].table[idx];" +
                "                  while(he) {" +
                "                       if (key==he->key || dictCompareKeys(d, key, he->key))" +
                "                               return he;" +
                "                       he = he->next;" +
                "                   }")
                .interpretation("拿到dt中对应下标存储的元素，如果key是一模一样的说明之前已经有了，否则，按照链表来查找，这里也可看出来，其实dict是通过链表来解决hash冲突");
        Code.SLICE.source("if (!dictIsRehashing(d)) return NULL;")
                .interpretation("如果不是进行rehash,另一个链表肯定不会有数据，说明没有找到");
        //...
    }

    @Trace(
            index = 9,
            originClassName = "dict.c",
            function = "int dictAdd(dict *d, void *key, void *val)"
    )
    public void dictAdd(){
        Code.SLICE.source("dictEntry *entry = dictAddRaw(d,key,NULL);")
                .interpretation("建立新的dictEntry,设置key值");
        Code.SLICE.source("if (!entry) return DICT_ERR;")
                .interpretation("如果这个key已经存在，抛出异常");
        Code.SLICE.source("dictSetVal(d, entry, val);")
                .interpretation("将值也放入entry中");

    }

    @Trace(
            index = 10,
            originClassName = "dict.c",
            function = "dictEntry *dictAddRaw(dict *d, void *key, dictEntry **existing)"
    )
    public void dictAddRaw(){
        //...
        Code.SLICE.source("if (dictIsRehashing(d)) _dictRehashStep(d);")
                .interpretation("如果dict正在执行Rehash先执行一步rehash");
        Code.SLICE.source("if ((index = _dictKeyIndex(d, key, dictHashKey(d,key), existing)) == -1)" +
                         "        return NULL;")
                .interpretation("计算出当前key在dict中的下标,如果在那个下标已经有这个key了，返回添加失败");
        Code.SLICE.source("ht = dictIsRehashing(d) ? &d->ht[1] : &d->ht[0];")
                .interpretation("根据是否在rehash来保证新的元素只会放在心的entry列表里面");
        Code.SLICE.source(" entry = zmalloc(sizeof(*entry));")
                .interpretation("分配新的entry的空间");
        Code.SLICE.source(" entry->next = ht->table[index];" +
                        "    ht->table[index] = entry;" +
                        "    ht->used++;")
                .interpretation("将新的entry放在第一个dict链表的第一位,并增加使用量");
        Code.SLICE.source(" dictSetKey(d, entry, key);")
                .interpretation("把key存入entry");
    }
    @Trace(
            index = 11,
            originClassName = "dict.c",
            function = "static long _dictKeyIndex(dict *d, const void *key, uint64_t hash, dictEntry **existing)"
    )
    public void _dictKeyIndex(){
        //...
        Code.SLICE.source("if (_dictExpandIfNeeded(d) == DICT_ERR)")
                .interpretation("如果dict需要扩容，先进行扩容");
        Code.SLICE.source(" for (table = 0; table <= 1; table++) {" +
                "        idx = hash & d->ht[table].sizemask;" +
                "        /* Search if this slot does not already contain the given key */" +
                "        he = d->ht[table].table[idx];" +
                "        while(he) {" +
                "            if (key==he->key || dictCompareKeys(d, key, he->key)) {" +
                "                if (existing) *existing = he;" +
                "                return -1;" +
                "            }" +
                "            he = he->next;" +
                "        }" +
                "        if (!dictIsRehashing(d)) break;" +
                "    }")
                .interpretation("计算出索引之后，看下有没有一模一样的key,有就返回-1，否则返回计算结果应该在的索引地址");
        //...
    }

    @Trace(
            index = 12,
            originClassName = "dict.c",
            function = "static int _dictExpandIfNeeded(dict *d)"
    )
    public void _dictExpandIfNeeded(){
        Code.SLICE.source("if (dictIsRehashing(d)) return DICT_OK;")
                .interpretation("如果已经在rehash了，那么不需要再次扩容");
        Code.SLICE.source("if (d->ht[0].size == 0) return dictExpand(d, DICT_HT_INITIAL_SIZE);")
                .interpretation("如果dict当前没有分配空间，默认扩容为为4个数组长度");
        Code.SLICE.source("  if (d->ht[0].used >= d->ht[0].size &&" +
                "        (dict_can_resize ||" +
                "         d->ht[0].used/d->ht[0].size > dict_force_resize_ratio))")
                .interpretation("当已经使用的量不小于分配的量，并且比例已经超过默认占比(默认值为5)进行扩容或者可以进行resize");
        Code.SLICE.source(" return dictExpand(d, d->ht[0].used*2);")
                .interpretation("扩容为使用量的2倍");
        //...
    }

    @Trace(
            index = 13,
            originClassName = "dict.c",
            function = "int dictExpand(dict *d, unsigned long size)"
    )
    public void dictExpand(){
        Code.SLICE.source(" if (dictIsRehashing(d) || d->ht[0].used > size)" +
                        "        return DICT_ERR;")
                .interpretation("如果已经rehash了，或者传入参数比现有的元素还要小，那么返回异常");
        Code.SLICE.source("unsigned long realsize = _dictNextPower(size);")
                .interpretation("计算出扩容后所需要的大小,默认2的幂次方");
        //..
        Code.SLICE.source(" n.size = realsize;" +
                        "    n.sizemask = realsize-1;" +
                        "    n.table = zcalloc(realsize*sizeof(dictEntry*));" +
                        "    n.used = 0;")
                .interpretation("记下当前扩容的大小，以及对应的掩码,分配新hashtable元素需要的空间，标记为尚未新增元素");
        //...
        Code.SLICE.source(" d->ht[1] = n;" +
                "    d->rehashidx = 0;")
                .interpretation("将新建的hash表放到原来dict的下标1的数组中，后续新增都会放入这里，并标记dict正在rehash");
        //...
    }

    @Trace(
            index = 14,
            originClassName = "dict.c",
            function = "static void _dictRehashStep(dict *d) "
    )
    public void _dictRehashStep(){
        Code.SLICE.source("if (d->iterators == 0) dictRehash(d,1);")
                .interpretation("没有迭代器在遍历的时候，才执行rehash");
    }
    @Trace(
            index = 15,
            originClassName = "dict.c",
            function = "int dictRehash(dict *d, int n)  ",
            introduction = "n是当前需要进行rehash的步数，如果rehash完成返回0否则返回1"
    )
    public void dictRehash(){
        Code.SLICE.source("int empty_visits = n*10; /* Max number of empty buckets to visit. */")
                .interpretation("不进行转移元素，最多访问的次数");
        //...
        Code.SLICE.source("while(d->ht[0].table[d->rehashidx] == NULL) {" +
                "            d->rehashidx++;" +
                "            if (--empty_visits == 0) return 1;" +
                "        }")
                .interpretation("遍历数组的下标，一直到有元素为止，如果超过了空跑的次数，先结束这次");
        Code.SLICE.source(" de = d->ht[0].table[d->rehashidx];" +
                "        /* Move all the keys in this bucket from the old to the new hash HT */" +
                "        while(de) {" +
                "            uint64_t h;" +
                "" +
                "            nextde = de->next;" +
                "            /* Get the index in the new hash table */" +
                "            h = dictHashKey(d, de->key) & d->ht[1].sizemask;" +
                "            de->next = d->ht[1].table[h];" +
                "            d->ht[1].table[h] = de;" +
                "            d->ht[0].used--;" +
                "            d->ht[1].used++;" +
                "            de = nextde;" +
                "        }")
                .interpretation("拿到旧数组中的元素，把这个元素的整个链表全部迁移到新的表中去，对应元素做加减");
        Code.SLICE.source("d->ht[0].table[d->rehashidx] = NULL;" +
                "        d->rehashidx++;")
                .interpretation("去掉原来的引用，并移动到下一个需要获取元素的位置");
        //...
        Code.SLICE.source(" if (d->ht[0].used == 0) {" +
                "        zfree(d->ht[0].table);" +
                "        d->ht[0] = d->ht[1];" +
                "        _dictReset(&d->ht[1]);" +
                "        d->rehashidx = -1;" +
                "        return 0;" +
                "    }")
                .interpretation("如果旧的表已经迁移完成，就更新为rehash完毕");
    }




















}
