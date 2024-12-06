const AWS = require('aws-sdk');
const secretsManager = new AWS.SecretsManager();

exports.handler = async (event) => {
    try {
        const keyInfo = await secretsManager.getSecretValue({ SecretId: event.secretName }).promise();

        let secret;
        if (data.SecretString) {
            secret = JSON.parse(keyInfo.SecretString);
        } else {
            secret = JSON.parse(Buffer.from(keyInfo.SecretBinary, 'base64').toString('utf-8'));
        }

        const accessKey = secret.aws_access_key_id;
        const secretKey = secret.aws_secret_access_key;
        const sessionToken = secret.aws_session_token;

        const credentials = new AWS.Credentials(accessKey, secretKey, sessionToken);

        const cloudwatch = new AWS.CloudWatch({ credentials: credentials, region: event.region });

        var newStartDate = new Date(event.startDate);
        newStartDate.setMinutes(event.startDate.getMinutes() - 2);

        const params = {
            Namespace: "AWS/RDS",
            MetricName: event.metricName,
            Dimensions: [
                {
                    Name: "DBInstanceIdentifier",
                    Value: event.currDbId
                }
            ],
            StartTime: newStartDate,
            Period: 60, 
            Statistics: event.dataStatistics 
        };

        const metrics = await cloudwatch.getMetricStatistics(params).promise();

        return { statusCode: 200, body: JSON.stringify(metrics) };
    } catch (error) {
        return { statusCode: 500, body: JSON.stringify(error.message) };
    }
}