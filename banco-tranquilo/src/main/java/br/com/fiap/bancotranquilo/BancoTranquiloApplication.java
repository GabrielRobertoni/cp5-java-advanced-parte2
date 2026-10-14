package br.com.fiap.bancotranquilo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BancoTranquiloApplication {

    public static void main(String[] args) {
        SpringApplication.run(BancoTranquiloApplication.class, args);
    }
}
