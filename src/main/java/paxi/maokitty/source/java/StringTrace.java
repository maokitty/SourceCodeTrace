package paxi.maokitty.source.java;

import paxi.maokitty.source.annotation.Background;
import paxi.maokitty.source.annotation.OneFunctionTrace;
import paxi.maokitty.source.annotation.Param;
import paxi.maokitty.source.util.Code;

/**
 * Created by maokitty on 19/6/26.
 */
@Background(
        target = "了解String中的部分源码是如何设计的",
        conclusion = "",
        sourceCodeProjectName = "java",
        sourceCodeAddress = "todo 补充地址",
        projectVersion = "1.7"

)
public class StringTrace {
    @OneFunctionTrace(
            originClassName = "java.lang.String",
            name = "static int indexOf(char[] source, int sourceOffset, int sourceCount, char[] target, int targetOffset, int targetCount,int fromIndex)",
            params = {@Param(name = "source",desc = "被搜索的字符串")
                     ,@Param(name = "sourceOffset",desc = "开始搜索目标字符串的位置")
                    ,@Param(name = "sourceCount",desc = "可被搜索区域的字符串长度,注意不一定等于source本身的长度")
                    ,@Param(name = "target",desc = "要查询的字符")
                    ,@Param(name = "targetOffset",desc = "要开始查的字符串的起点")
                    ,@Param(name = "targetCount",desc = "要查找的字符串的个数，注意不一定等于target本身的长度")
                    ,@Param(name = "fromIndex",desc = "从开始搜索的目标字符串位置往后偏移fromIndex的位置")}
    )
    public void lastIndexOf(){
        Code.SLICE.source("if (fromIndex >= sourceCount) {" +
                "            return (targetCount == 0 ? sourceCount : -1);" +
                "        }")
                .interpretation("1：如果偏移的位置超过了总共可以搜索的字符数，肯定找不到，假设 总公共可以搜索的字符串只有3个，但是偏要从第4个位置开始找，肯定找不到")
                .interpretation("2：约定如果要查找的字符串是0个，那么返回可搜索的字符串长度，否则返回-1表示没有找到");
        Code.SLICE.source(" if (fromIndex < 0) {" +
                "            fromIndex = 0;" +
                "        }" +
                "        if (targetCount == 0) {" +
                "            return fromIndex;" +
                "        }")
                .interpretation("1：如果指定搜索的位置是负数，修改为从0，也就是默认从头开始找")
                .interpretation("2:如果要找的字符串的长度是0，那么约定返回开始找的位置");
        Code.SLICE.source("char first = target[targetOffset];")
                .interpretation("1:获取第一个要搜索的位置的字符");
        Code.SLICE.source("int max = sourceOffset + (sourceCount - targetCount);")
                .interpretation("假设从偏移量为0的位置开始，可以搜索的字符是5个，目标字串是2个，那么只需要找到下标为3的字符(此时已经是最后一个了)，如果还不是，那么剩下的就只有1个字符了，不可能找到 targetCount 个字符");
        Code.SLICE.source("for (int i = sourceOffset + fromIndex; i <= max; i++)")
                .interpretation("从指定的第一个位置开始搜索,一直到最后可搜索的字符结束");
        Code.SLICE.source("if (source[i] != first) {" +
                "                while (++i <= max && source[i] != first);" +
                "            }")
                .interpretation("一直找第一个字符一模一样的位置，知道找到最后的一个位置，如果还没有说明没有");
        Code.SLICE.source("if (i <= max) {" +
                "                int j = i + 1;" +
                "                int end = j + targetCount - 1;" +
                "                for (int k = targetOffset + 1; j < end && source[j]" +
                "                        == target[k]; j++, k++);" +
                "" +
                "                if (j == end) {" +
                "                    /* Found whole string. */" +
                "                    return i - sourceOffset;" +
                "                }" +
                "            }")
                .interpretation("说明存在第一个字符一模一样的，这个时候，开始从第一个找的位置的下一个位置开始找第二个字符，一直匹配到目标字串的长度。如果已经匹配到了最后一个字符，那么说明找到了，开始的位置就是从可搜索位置到当前位置的距离");
        //...
    }

}
