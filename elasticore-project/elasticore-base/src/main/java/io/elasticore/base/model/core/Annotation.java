package io.elasticore.base.model.core;

import lombok.Getter;

import java.util.*;

@Getter
public class Annotation {

    public static int TYPE_NO_VALUE = 0;
    public static int TYPE_SINGLE_VALUE = 1;
    public static int TYPE_KEY_VALUE = 2;

    private String name;

    private int type = TYPE_NO_VALUE;


    public String value;

    private Properties properties;

    private List<Annotation> siblings = new ArrayList<>();

    private Annotation(String name) {
        this.name = name.toLowerCase();
        this.type = TYPE_NO_VALUE;
        this.siblings.add(this);
    }

    private Annotation(String name, String value) {
        this.name = name.toLowerCase();
        this.value = value;
        this.type = TYPE_SINGLE_VALUE;
        this.siblings.add(this);
    }

    private Annotation(String name, Properties properties) {
        this.name = name.toLowerCase();
        this.properties = properties;
        this.type = TYPE_KEY_VALUE;
        this.siblings.add(this);
    }

    /** True for a label whose parameters are its translations (LabelTranslations). */
    private boolean translatedLabel;

    private Annotation(String name, String value, Properties properties) {
        this(name, properties);
        this.value = value;
        this.translatedLabel = true;
    }

    public static Annotation create(String name) {
        return new Annotation(name);
    }

    /**
     * A label and its translations: the value and language=text parameters
     * together (LabelTranslations). {@link #create(String, String, Properties)}
     * keeps only the parameters when both are given, as it always has.
     */
    public static Annotation createTranslatedLabel(String name, String value, Properties translations) {
        return new Annotation(name, value, translations);
    }

    /** True when the parameters are the label in other languages, not 2.x key=value pairs. */
    public boolean isTranslatedLabel() {
        return translatedLabel;
    }

    public static Annotation create(String name, String value) {

        return create(name,value,null);
    }
    public static Annotation create(String name, String value, Properties properties) {
        if(properties!=null && properties.size()>0)
            return new Annotation(name, properties);
        if(value !=null)
            return new Annotation(name, value);

        return new Annotation(name);
    }

    public String getValue(){
        if(this.value!=null) {
            if(this.value.indexOf("'")==0)
                if(this.value.lastIndexOf("'")==(this.value.length()-1))
                    return this.value.substring(1, this.value.length()-1);
            if(this.value.indexOf("\"")==0)
                if(this.value.lastIndexOf("\"")==(this.value.length()-1))
                    return this.value.substring(1, this.value.length()-1);
        }
        return this.value;
    }

    public boolean hasValue() {
        if(this.value == null)
            return false;

        return true;
    }

    public Properties getProperties() {
        return this.properties;
    }

    @Override
    public String toString() {
        // do not change this.
        return value;
    }


    public List<Annotation> getSiblings() {
        return siblings;
    }


    public static Map<String,Annotation> createAnnotationMap(){
        Map<String, Annotation> annotationMap = new HashMap<>();
        annotationMap.put("uuid", Annotation.create("uuid",UUID.randomUUID().toString()));
        return annotationMap;
    }


}
