package tsubota1991tech.github.io.aws_game_manager.aws;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import tsubota1991tech.github.io.aws_game_manager.domain.CloudAccount;

@Component
public class AwsClientFactory {

    public Ec2Client createEc2Client(CloudAccount account) {
        return Ec2Client.builder()
                .region(Region.of(account.getDefaultRegion()))
                .credentialsProvider(buildCredentialsProvider(account))
                .build();
    }

    public AutoScalingClient createAutoScalingClient(CloudAccount account) {
        return AutoScalingClient.builder()
                .region(Region.of(account.getDefaultRegion()))
                .credentialsProvider(buildCredentialsProvider(account))
                .build();
    }

    public SqsClient createSqsClient(String region, String accessKeyId, String secretAccessKey) {
        AwsCredentialsProvider provider = StringUtils.hasText(accessKeyId) && StringUtils.hasText(secretAccessKey)
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
                : DefaultCredentialsProvider.create();

        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(provider)
                .build();
    }

    private AwsCredentialsProvider buildCredentialsProvider(CloudAccount account) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                account.getAwsAccessKeyId(),
                account.getAwsSecretAccessKey()
        );
        return StaticCredentialsProvider.create(credentials);
    }
}
