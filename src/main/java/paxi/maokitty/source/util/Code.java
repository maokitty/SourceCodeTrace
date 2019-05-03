package paxi.maokitty.source.util;

/**
 * Created by maokitty on 19/5/3.
 */
public final class Code {
    public  static final Slice SLICE = new Slice();

    public  static final class Slice{
        private String codeInterpretation;
        private String codeDetail;

        public Slice interpretation(String interpretation){
            codeInterpretation=interpretation;
            return this;
        }

        public Slice source(String code){
            codeDetail=code;
            return this;
        }
    }
}
