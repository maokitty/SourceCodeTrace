/**
 * @(#)BgrewriteaofCommandExecuteTrace.java, 4月 07, 2020.
 * <p>
 * Copyright 2020 fenbi.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package paxi.maokitty.source.redis;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.Main;
import paxi.maokitty.source.annotation.Trace;
import paxi.maokitty.source.util.Code;

/**
 * @author maokitty
 */
@Background(
        target = "了解用户执行重写AOF命令的机制",
        conclusion = "1: 重写就是把当前内存所有的数据1条条的写到新的临时文件，最后在serverCron中重定向到  AOF 文件中；2：重写是异步的",
        sourceCodeProjectName = "redis",
        sourceCodeAddress = "https://github.com/antirez/redis",
        projectVersion = "5.0.0"

)
public class BgRewriteAofCommandExecuteTrace {

    @Trace(
            index = 0,
            originClassName = "aof.c",
            function = "void bgrewriteaofCommand(client *c)"
    )
    @Main
    public void bgrewriteaofCommand(){
        //..
        Code.SLICE.source("} else if (rewriteAppendOnlyFileBackground() == C_OK) ")
                .interpretation("当没有rewrite正在执行并且也没有定时任务时，开始执行 rewrite");
        //..
    }
    @Trace(
            index = 1,
            originClassName = "aof.c",
            function = "int rewriteAppendOnlyFileBackground(void)"
    )
    public void rewriteAppendOnlyFileBackground(){
        //..
        Code.SLICE.source("if (aofCreatePipes() != C_OK) return C_ERR;" +
                "               openChildInfoPipe();")
                .interpretation("创建父子通信的管道");
        //..
        Code.SLICE.source("if ((childpid = redisFork()) == 0) {" +
                "        char tmpfile[256];" + "" +
                "        /* Child */" +
                "        redisSetProcTitle(\"redis-aof-rewrite\");" +
                "        snprintf(tmpfile,256,\"temp-rewriteaof-bg-%d.aof\", (int) getpid());" +
                "        if (rewriteAppendOnlyFile(tmpfile) == C_OK) {" +
                "            sendChildCOWInfo(CHILD_INFO_TYPE_AOF, \"AOF rewrite\");" +
                "            exitFromChild(0);" +
                "        } else {" +
                "            exitFromChild(1);" +
                "        }" +
                "    } else {" +
                "        /* Parent */" +
                "       //..." +
                "        serverLog(LL_NOTICE," +
                "            \"Background append only file rewriting started by pid %d\",childpid);" +
                "        server.aof_rewrite_scheduled = 0;" +
                "        server.aof_rewrite_time_start = time(NULL);" +
                "        server.aof_child_pid = childpid;" +
                "        /* We set appendseldb to -1 in order to force the next call to the" +
                "         * feedAppendOnlyFile() to issue a SELECT command, so the differences" +
                "         * accumulated by the parent into server.aof_rewrite_buf will start" +
                "         * with a SELECT statement and it will be safe to merge. */" +
                "        server.aof_selected_db = -1;" +
                "        replicationScriptCacheFlush();" +
                "        return C_OK;" + "    }")
                .interpretation("fork子进程处理命令");
    }

    @Trace(
            index = 2,
            originClassName = "aof.c",
            function = "int rewriteAppendOnlyFile(char *filename) ",
            more = "AppendOnlyFileAfterExecuteCommandTrace 中的发送机制与这里存在交互"
    )
    public void rewriteAppendOnlyFile(){
        //..
        Code.SLICE.source("snprintf(tmpfile,256,\"%d.aof\", (int) getpid());" +
                "    fp = fopen(tmpfile,\"w\");")
                .interpretation("创建临时aof文件");
        //..
        Code.SLICE.source("if (rewriteAppendOnlyFileRio(&aof) == C_ERR) goto werr;")
                .interpretation("开始写");
        //...
        Code.SLICE.source("if (fflush(fp) == EOF) goto werr;" +
                "    if (fsync(fileno(fp)) == -1) goto werr;")
                .interpretation("将文件写入磁盘");
        //..
        Code.SLICE.source(" while(mstime()-start < 1000 && nodata < 20) {" +
                "        if (aeWait(server.aof_pipe_read_data_from_parent, AE_READABLE, 1) <= 0)" +
                "        {" +
                "            nodata++;" +
                "            continue;" +
                "        }" +
                "        nodata = 0; /* Start counting from zero, we stop on N *contiguous*" +
                "                       timeouts. */" +
                "        aofReadDiffFromParent();" +
                "    }")
                .interpretation("去父进程读取数据，直到到达读取的阈值,将读取到的数据放入 aof_child_diff 中");
        //..
        Code.SLICE.source("if (write(server.aof_pipe_write_ack_to_parent,\"!\",1) != 1) goto werr;")
                .interpretation("往父进程发送停止同步写入数据的标记，父进程在读到这个 ! 标记后，会把 aof_stop_sending_diff 置为1 ");
        //...
        Code.SLICE.source("aofReadDiffFromParent();")
                .interpretation("最后再读取一次");
        //..
        Code.SLICE.source("if (rioWrite(&aof,server.aof_child_diff,sdslen(server.aof_child_diff)) == 0)")
                .interpretation("将追加的内容写入文件");
        //..
        Code.SLICE.source("if (fflush(fp) == EOF) goto werr;" +
                "    if (fsync(fileno(fp)) == -1) goto werr;" +
                "    if (fclose(fp) == EOF) goto werr;")
                .interpretation("进行最后一次的同步，并关闭文件");
        //..
        Code.SLICE.source("rename(tmpfile,filename)")
                .interpretation("文件重命名成temp-rewriteaof-bg-%d.aof的文件，rename操作为原子,这里的 %d 就是进程编号")
                .interpretation("1：注意，这里并没有把文件重命名成 aof_filename 指定的文件名,操作需要在定时任务中完成");

        //..
    }

    @Trace(
            index = 3,
            originClassName = "aof.c",
            function = "int rewriteAppendOnlyFileRio(rio *aof) "
    )
    public void rewriteAppendOnlyFileRio(){
        //..
        Code.SLICE.source("for (j = 0; j < server.dbnum; j++)")
                .interpretation("遍历所有的数据库");
        Code.SLICE.source("char selectcmd[] = \"*2\\r\\n$6\\r\\nSELECT\\r\\n\";" +
                "        redisDb *db = server.db+j;" +
                "        dict *d = db->dict;" +
                "        if (dictSize(d) == 0) continue;" +
                "        di = dictGetSafeIterator(d);" + "" +
                "        /* SELECT the new DB */" +
                "        if (rioWrite(aof,selectcmd,sizeof(selectcmd)-1) == 0) goto werr;" +
                "        if (rioWriteBulkLongLong(aof,j) == 0) goto werr;")
                .interpretation("记下当前数据存的数据库")
                .interpretation("1: 数据库没有数据（key数量为0）执行下一个")
                .interpretation("2: 拿到数据库中所有的 key");
        Code.SLICE.source(" keystr = dictGetKey(de);" +
                "            o = dictGetVal(de);" +
                "       //.." +
                "           else if (o->type == OBJ_SET) {" +
                "                if (rewriteSetObject(aof,&key,o) == 0) goto werr;" +
                "            } " +
                        "//.." +
                "")
                .interpretation("遍历 每一个key和 它 的value,按照value的类型拿到所有的值按照  AOF 的协议写入磁盘");
        Code.SLICE.source("if (expiretime != -1) {" +
                "                char cmd[]=\"*3\\r\\n$9\\r\\nPEXPIREAT\\r\\n\";" +
                "                if (rioWrite(aof,cmd,sizeof(cmd)-1) == 0) goto werr;" +
                "                if (rioWriteBulkObject(aof,&key) == 0) goto werr;" +
                "                if (rioWriteBulkLongLong(aof,expiretime) == 0) goto werr;" +
                "            }")
                .interpretation("命令存在过期时间，再追加1条过期的命令 ");
        Code.SLICE.source("if (aof->processed_bytes > processed+AOF_READ_DIFF_INTERVAL_BYTES) {" +
                "                processed = aof->processed_bytes;" +
                "                aofReadDiffFromParent();" +
                "            }")
                .interpretation("每读取一点数据就从父进程读取一部分新增的数据到子进程");
    }

    @Trace(
            index = 4,
            originClassName = "aof.c",
            function = "ssize_t aofReadDiffFromParent(void) "
    )
    public void  aofReadDiffFromParent(){
        //..
        Code.SLICE.source("while ((nread =" +
                "            read(server.aof_pipe_read_data_from_parent,buf,sizeof(buf))) > 0) {" +
                "        server.aof_child_diff = sdscatlen(server.aof_child_diff,buf,nread);" +
                "        total += nread;" + "    }")
                .interpretation("从父进程读取一部分差异的数据存到到 aof_child_diff");
    }

}
