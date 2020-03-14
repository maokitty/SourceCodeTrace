package paxi.maokitty.source.redis;

import paxi.maokitty.source.annotation.*;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 20/3/11.
 */
@Background(
        target = "了解用户调用了sadd命令之后，redis执行的过程，以及底层对数据结构的实现",
        conclusion = "1:set 底层使用了两种结构 intset和hashtable ;2:intset 内部是按照升序排列；3：intset根据数值大小会分成不同的数据结构，方便节省空间",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"
)
public class SaddCommandExecuteTrace {

        @Main
        @Trace(
                index= 0,
                originClassName = "t_set.c",
                function = "void saddCommand(client *c) "
        )
        public void saddCommand(){
                //..
                Code.SLICE.source("set = lookupKeyWrite(c->db,c->argv[1]);")
                        .interpretation("查看db中是否有存储当前要插入的key");

                Code.SLICE.source("if (set == NULL) {\n" +
                        "        set = setTypeCreate(c->argv[2]->ptr);\n" +
                        "        dbAdd(c->db,c->argv[1],set);\n" +
                        "    } else {\n" +
                        "        if (set->type != OBJ_SET) {\n" +
                        "            addReply(c,shared.wrongtypeerr);\n" +
                        "            return;\n" +
                        "        }\n" +
                        "    }")
                        .interpretation("如果要增加的key不存在，则新加并存储，否则查看原有key的类型是否是 set 不是说明共享了同一个key在不同的结构，抛出异常");
                Code.SLICE.source("for (j = 2; j < c->argc; j++) {\n" +
                        "        if (setTypeAdd(set,c->argv[j]->ptr)) added++;\n" +
                        "    }")
                        .interpretation("逐个的添加集合总需要添加的元素");
                //..
        }

        @KeyPoint
        @Trace(
                index= 1,
                originClassName = "t_set.c",
                function = "robj *setTypeCreate(sds value)"
        )
        public void setTypeCreate(){
              Code.SLICE.source("robj *setTypeCreate(sds value) {\n" +
                      "    if (isSdsRepresentableAsLongLong(value,NULL) == C_OK)\n" +
                      "        return createIntsetObject();\n" +
                      "    return createSetObject();\n" +
                      "}")
                      .interpretation("看set中要添加的值是否能够转成long long类型，如果可以，set的类型为IntSet,否则使用hash table");
        }


        @Trace(
                index= 2,
                originClassName = "t_set.c",
                function = "int setTypeAdd(robj *subject, sds value) ",
                more = "往hashtable中添加数据可见 HsetCommandExecuteTrace"
        )
        public void setTypeAdd(){
                Code.SLICE.source("if (subject->encoding == OBJ_ENCODING_HT) {\n" +
                        "        dict *ht = subject->ptr;\n" +
                        "        dictEntry *de = dictAddRaw(ht,value,NULL);\n" +
                        "        if (de) {\n" +
                        "            dictSetKey(ht,de,sdsdup(value));\n" +
                        "            dictSetVal(ht,de,NULL);\n" +
                        "            return 1;\n" +
                        "        }\n" +
                        "    }")
                        .interpretation("处理使用hash table存储的情况")
                        .interpretation("1: 传进来的key对象它的指针即指向存储结构hashtable")
                        .interpretation("2：往hashtable中插入key和value即可");
                Code.SLICE.source("else if (subject->encoding == OBJ_ENCODING_INTSET) {\n" +
                        "        if (isSdsRepresentableAsLongLong(value,&llval) == C_OK) {\n" +
                        "            uint8_t success = 0;\n" +
                        "            subject->ptr = intsetAdd(subject->ptr,llval,&success);\n" +
                        "            if (success) {\n" +
                        "                /* Convert to regular set when the intset contains\n" +
                        "                 * too many entries. */\n" +
                        "                if (intsetLen(subject->ptr) > server.set_max_intset_entries)\n" +
                        "                    setTypeConvert(subject,OBJ_ENCODING_HT);\n" +
                        "                return 1;\n" +
                        "            }\n" +
                        "        } else {\n" +
                        "            /* Failed to get integer from object, convert to regular set. */\n" +
                        "            setTypeConvert(subject,OBJ_ENCODING_HT);\n" +
                        "\n" +
                        "            /* The set *was* an intset and this value is not integer\n" +
                        "             * encodable, so dictAdd should always work. */\n" +
                        "            serverAssert(dictAdd(subject->ptr,sdsdup(value),NULL) == DICT_OK);\n" +
                        "            return 1;\n" +
                        "        }\n" +
                        "    }")
                        .interpretation("set的另外一种数据结构，intset ,只要当前数据还能够转换成 longlong,那么继续在set中增加，否则将结构转换成 hashtable")
                        .interpretation("1: 往intset添加成功之后，如果集合的元素个数已经超过了 配置的 set_max_intset_entries ，那么转换成 hashtable");
                //...

        }

        @Trace(
                index= 3,
                originClassName = "intset.c",
                function = "intset *intsetAdd(intset *is, int64_t value, uint8_t *success)"
        )
        public void intsetAdd(){
               Code.SLICE.source("uint8_t valenc = _intsetValueEncoding(value);")
                      .interpretation("看要插入值的大小，安排不同的编码方式，具体包括：INTSET_ENC_INT64 INTSET_ENC_INT32 INTSET_ENC_INT16");
                //..
                Code.SLICE.source("if (valenc > intrev32ifbe(is->encoding)) {\n" +
                        "        /* This always succeeds, so we don't need to curry *success. */\n" +
                        "        return intsetUpgradeAndAdd(is,value);\n" +
                        "    } ")
                        .interpretation("当插入的值大于当前编码方式的值，需要进行升级");
                Code.SLICE.source(" else {\n" +
                        "        /* Abort if the value is already present in the set.\n" +
                        "         * This call will populate \"pos\" with the right position to insert\n" +
                        "         * the value when it cannot be found. */\n" +
                        "        if (intsetSearch(is,value,&pos)) {\n" +
                        "            if (success) *success = 0;\n" +
                        "            return is;\n" +
                        "        }\n" +
                        "\n" +
                        "        is = intsetResize(is,intrev32ifbe(is->length)+1);\n" +
                        "        if (pos < intrev32ifbe(is->length)) intsetMoveTail(is,pos,pos+1);\n" +
                        "    }")
                        .interpretation("当前值没超过当前的编码方式")
                        .interpretation("1: 如果集合中有这个值，那么什么都不做,否则拿到了要插入的位置")
                        .interpretation("2: 将原有的集合增加1个位置")
                        .interpretation("3: 如果发现要插入的位置在末尾，移动编码方式对应的距离直接到末尾即可");
                Code.SLICE.source("_intsetSet(is,pos,value);")
                        .interpretation("在确定位置插入值");
                //...
        }

        @KeyPoint
        @Trace(
                index= 4,
                originClassName = "intset.c",
                function = "static uint8_t intsetSearch(intset *is, int64_t value, uint32_t *pos)"
        )
        public void intsetSearch(){
            Code.SLICE.source("int min = 0, max = intrev32ifbe(is->length)-1, mid = -1;")
                    .interpretation("先记下最小值和最大值的下标");
            Code.SLICE.source("  if (intrev32ifbe(is->length) == 0) {\n" +
                    "        if (pos) *pos = 0;\n" +
                    "        return 0;\n" +
                    "    } else {\n" +
                    "        /* Check for the case where we know we cannot find the value,\n" +
                    "         * but do know the insert position. */\n" +
                    "        if (value > _intsetGet(is,intrev32ifbe(is->length)-1)) {\n" +
                    "            if (pos) *pos = intrev32ifbe(is->length);\n" +
                    "            return 0;\n" +
                    "        } else if (value < _intsetGet(is,0)) {\n" +
                    "            if (pos) *pos = 0;\n" +
                    "            return 0;\n" +
                    "        }\n" +
                    "    }")
                    .interpretation("处理边界情况")
                    .interpretation("1: 如果集合中是空的，直接在开始插入即可")
                    .interpretation("2: 如果新插入的值小于当前最小的值，在开头插入即可")
                    .interpretation("3: 如果插入新值大于当前最大的值，在结尾插入即可");
            Code.SLICE.source("while(max >= min) {\n" +
                    "        mid = ((unsigned int)min + (unsigned int)max) >> 1;\n" +
                    "        cur = _intsetGet(is,mid);\n" +
                    "        if (value > cur) {\n" +
                    "            min = mid+1;\n" +
                    "        } else if (value < cur) {\n" +
                    "            max = mid-1;\n" +
                    "        } else {\n" +
                    "            break;\n" +
                    "        }\n" +
                    "    }")
                    .interpretation("二分查找，找到插入的位置，这里要么找到现有值元素的位置，要么找到要插入的位置");
            //...
        }

        @Trace(
                index= 5,
                originClassName = "intset.c",
                function = "static intset *intsetUpgradeAndAdd(intset *is, int64_t value) "
        )
        public void intsetUpgradeAndAdd(){
               Code.SLICE.source("uint8_t curenc = intrev32ifbe(is->encoding);\n" +
                       "    uint8_t newenc = _intsetValueEncoding(value);")
                       .interpretation("拿到当前的编码方式和新的编码方式");
               //..
               Code.SLICE.source("is->encoding = intrev32ifbe(newenc);")
                       .interpretation("首先变更set的编码方式为新的编码方式");
               //...
               Code.SLICE.source("while(length--)\n" +
                       "        _intsetSet(is,length+prepend,_intsetGetEncoded(is,length,curenc));")
                       .interpretation("将原有的值一个个的往后移");

                Code.SLICE.source("if (prepend)\n" +
                        "        _intsetSet(is,0,value);\n" +
                        "    else\n" +
                        "        _intsetSet(is,intrev32ifbe(is->length),value);\n")
                        .interpretation("升级的时候，要插入的元素要么在头部要么在尾部");
               //..
        }

        @Trace(
                index= 7,
                originClassName = "t_set.c",
                function = "void setTypeConvert(robj *setobj, int enc) "
        )
        public void setTypeConvert(){
                Code.SLICE.source("serverAssertWithInfo(NULL,setobj,setobj->type == OBJ_SET &&\n" +
                        "                             setobj->encoding == OBJ_ENCODING_INTSET);")
                        .interpretation("确保升级的是set类型，并且它原来的编码方式是intset");
                Code.SLICE.source("if (enc == OBJ_ENCODING_HT)")
                        .interpretation("仅处理升级成hashtable的方式");

                //..
                Code.SLICE.source("dict *d = dictCreate(&setDictType,NULL);")
                        .interpretation("新建一个字典结构");

                Code.SLICE.source("dictExpand(d,intsetLen(setobj->ptr));")
                        .interpretation("申请足够的空间，确保不需要rehash");

                Code.SLICE.source("si = setTypeInitIterator(setobj);\n" +
                        "        while (setTypeNext(si,&element,&intele) != -1) {\n" +
                        "            element = sdsfromlonglong(intele);\n" +
                        "            serverAssert(dictAdd(d,element,NULL) == DICT_OK);\n" +
                        "        }")
                        .interpretation("遍历原有的intset结构，取到对应的元素值，一个的加入到字典中去");
                //..

        }

}
