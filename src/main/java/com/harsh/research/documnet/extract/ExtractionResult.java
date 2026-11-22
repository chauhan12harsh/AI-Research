package com.harsh.research.documnet.extract;

public record ExtractionResult(

        String text,
        Integer pageCount,
        int charCount

) {

    public static ExtractionResult of(String text,Integer pageCount){
        return new ExtractionResult(text,pageCount,text.length());
    }
}
