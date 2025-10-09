//package com.vn.ecm.ecm.s3;
//
//import com.google.common.base.Strings;
//import com.vn.ecm.entity.SourceStorage;
//
//import io.jmix.core.*;
//
//import jakarta.validation.constraints.NotNull;
//import org.apache.commons.lang3.StringUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.context.event.ApplicationStartedEvent;
//import org.springframework.context.event.EventListener;
//import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
//import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
//import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
//import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
//import software.amazon.awssdk.services.s3.S3Client;
//import io.jmix.core.common.util.Preconditions;
//import software.amazon.awssdk.services.s3.S3ClientBuilder;
//import software.amazon.awssdk.regions.Region;
//
//import java.io.InputStream;
//import java.net.URI;
//import org.apache.commons.io.FilenameUtils;
//
//import java.util.Calendar;
//import java.util.Map;
//import java.util.concurrent.atomic.AtomicReference;
//
//
//public class s3FileStorage implements FileStorage {
//
//    private static final Logger log = LoggerFactory.getLogger(s3FileStorage.class);
//
//    protected String storageName;
//    @Autowired
//    protected SourceStorage properties;
//    boolean useConfigurationProperties = true;
//
//    protected String accessKey;
//    protected String secretAccessKey;
//    protected String region;
//    protected String bucket;
//    protected int chunkSize;
//    protected String endpointUrl;
//    protected boolean usePathStyleBucketAddressing;
//
//    @Autowired
//    protected TimeSource timeSource;
//    protected AtomicReference<S3Client> s3ClientReference = new AtomicReference<>();
//
//    public s3FileStorage(){
//
//    }
//    public s3FileStorage(String storageName){
//        this.storageName = storageName;
//    }
//
//    public s3FileStorage(String storageName,
//                         String accessKey,
//                         String secretAccessKey,
//                         String region,
//                         String bucket,
//                         int chunkSize,
//                         @NotNull
//                         String endpointUrl) {
//        this.storageName = storageName;
//        this.accessKey = accessKey;
//        this.secretAccessKey = secretAccessKey;
//        this.region = region;
//        this.bucket = bucket;
//        this.chunkSize = chunkSize;
//        this.endpointUrl = endpointUrl;
//    }
//    @EventListener
//    protected void initS3Client(ApplicationStartedEvent event){
//
//        //refreshS3Client();
//    }
//    protected void refeshSourceStorage(){
//        if(useConfigurationProperties){
//            this.accessKey = properties.getAccessKey();
//            this.secretAccessKey = properties.getSecretAccessKey();
//            this.region = properties.getRegion();
//            this.bucket = properties.getBucket();
//            this.chunkSize = properties.getChunkSize();
//            this.endpointUrl = properties.getEndpointUrl();
//        }
//    }
//    protected AwsCredentialsProvider getAwsCredentialsProvider(){
//        if(accessKey != null && secretAccessKey != null){
//            AwsCredentialsProvider awsCredentials = AwsBasicCredentials.create(accessKey, secretAccessKey);
//            return StaticCredentialsProvider.create(awsCredentials);
//        }else{
//            return DefaultCredentialsProvider.builder().build();
//        }
//    }
//    public void refreshS3Client(){
//        refeshSourceStorage();
//        Preconditions.checkNotEmptyString(bucket, "bucket must not be empty");
//        AwsCredentialsProvider awsCredentialsProvider = getAwsCredentialsProvider();
//        S3ClientBuilder s3ClientBuilder = S3Client.builder();
//        s3ClientBuilder.credentialsProvider(awsCredentialsProvider);
//        if (!Strings.isNullOrEmpty(region)) {
//            s3ClientBuilder.region(Region.of(region));
//        }
//        if (!Strings.isNullOrEmpty(endpointUrl)) {
//            s3ClientBuilder.endpointOverride(URI.create(endpointUrl));
//        }
//        s3ClientBuilder.forcePathStyle(usePathStyleBucketAddressing);
//        s3ClientReference.set(s3ClientBuilder.build());
//    }
//    @Override
//    public String getStorageName() {
//        return storageName;
//    }
//    protected String createFileKey(String fileName){
//        return createDateDir() + "/" + createUuidFilename(fileName);
//    }
//    protected String createDateDir(){
//        Calendar cal = Calendar.getInstance();
//        cal.setTime(timeSource.currentTimestamp());
//        int year = cal.get(Calendar.YEAR);
//        int month = cal.get(Calendar.MONTH) + 1;
//        int day = cal.get(Calendar.DAY_OF_MONTH);
//
//        return String.format("%d/%s/%s", year,
//                StringUtils.leftPad(String.valueOf(month), 2, '0'),
//                StringUtils.leftPad(String.valueOf(day), 2, '0'));
//    }
//
//    protected String createUuidFilename(String fileName){
//        String extention =  FilenameUtils.getExtension(fileName);
//        if(StringUtils.isNoneEmpty(extention)){
//            return UuidProvider.createUuid().toString() + "." + extention;
//        }else{
//            return UuidProvider.createUuid().toString();
//        }
//    }
//
//    @Override
//    public FileRef saveStream(String fileName , InputStream inputStream, Map<String, Object> parameters){
//        String fileKey = createFileKey()
//    }
//
//
//}
//
//
