package paxi.maokitty.source.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by maokitty on 19/5/3.
 */
public final class Code {
    public  static final Slice SLICE = new Slice();

    public  static final class Slice{
        private List<String> codeInterpretation;
        private String codeDetail;

        public Slice interpretation(String interpretation){
            if (codeInterpretation == null){
                codeInterpretation = new ArrayList<String>();
            }
            codeInterpretation.add(interpretation);
            return this;
        }

        public Slice source(String code){
            codeDetail=code;
            return this;
        }
    }
}
