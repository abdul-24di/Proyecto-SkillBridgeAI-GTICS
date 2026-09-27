package com.pucp.skillb_ia.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration //Le dice a Spring que este archivo contiene configuraciones del sistema
public class S3Config {

    private static final Logger log = LoggerFactory.getLogger(S3Config.class);

    //Trae la región desde el archivo application.properties, por defecto es us-east-1
    @Value("${aws.s3.region:us-east-1}")
    private String region;

    //Busca las llaves directamente en las variables de entorno de la computadora
    @Value("${AWS_ACCESS_KEY_ID:}")
    private String accessKeyId;

    @Value("${AWS_SECRET_ACCESS_KEY:}")
    private String secretAccessKey;

    //Buscamos el Token que es obligatorio por que la cuenta es de estudiante
    @Value("${AWS_SESSION_TOKEN:}")
    private String sessionToken;

    @Bean //Crea el cliente de S3 y lo guarda en la caja de herramientas de Spring
    public S3Client s3Client() {

        log.info("S3Config -> accessKeyId presente: {}, secretAccessKey presente: {}, sessionToken presente: {}",
                accessKeyId != null && !accessKeyId.isBlank(),
                secretAccessKey != null && !secretAccessKey.isBlank(),
                sessionToken != null && !sessionToken.isBlank());

        //Inicializa el armador (builder) del cliente apuntando a la región correcta
        var builder = S3Client.builder().region(Region.of(region));

        //Si el usuario configuró las llaves en su sistema, armamos las credenciales
        if (accessKeyId != null && !accessKeyId.isBlank() && secretAccessKey != null && !secretAccessKey.isBlank()) {
            //verificamos que existe el  token de sesión y usamos las 3 llaves
            if (sessionToken != null && !sessionToken.isBlank()) {

                var credenciales = AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);

                builder.credentialsProvider(StaticCredentialsProvider.create(credenciales));
            }
            //En caso de que no hay token, hacemos que se conecte solo con el acceso y la clave secreta
            else {
                var credenciales = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
                builder.credentialsProvider(StaticCredentialsProvider.create(credenciales));
            }
        }

        //Construimos y entregamos el cliente de S3 listo para operar
        return builder.build();
    }
}
