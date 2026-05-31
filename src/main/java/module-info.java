module com.aws.basic.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.core;
    requires jakarta.annotation;


    opens com.KeywordExtractor.basic;
    exports com.KeywordExtractor.basic;
}
