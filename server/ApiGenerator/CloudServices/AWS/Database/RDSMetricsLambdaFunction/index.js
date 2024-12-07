const AWS = require('aws-sdk');
const secretsManager = new AWS.SecretsManager();

exports.handler = async (event) => {
    if (event.source === "aws.rds") {
        if (event.dbInfo["status"] === "available") {
            await startMetricsPolling(event.dbInfo["ruleParams"], event.dbInfo["targetParams"], event.userInfo["secretName"], event.userInfo["userRegion"]);
        } else if (dbInstanceStatus === "stopped") {
            await stopMetricsPolling(event.metricsInfo["ruleName"], event.userInfo["secretName"], event.metricsInfo["targetIds"]);
        }
    } else if (event.action === "collectMetrics") {
        var metricsData = await getRDSMetrics(event.metricsInfo, event.userInfo["secretName"]);

        return { statusCode: 200, body: JSON.stringify(metricsData)};
    } else {
        return { statusCode: 400, body: "Unhandled event type" };
    }

    return { statusCode: 200, body: "Success" };
};

const getRDSMetrics = async (metricsInfo, secretName) => {
    try {
        const keyInfo = await secretsManager.getSecretValue({ SecretId: secretName }).promise();

        let secret;
        if (keyInfo.SecretString) {
            secret = JSON.parse(keyInfo.SecretString);
        } else {
            secret = JSON.parse(Buffer.from(keyInfo.SecretBinary, 'base64').toString('utf-8'));
        }

        const accessKey = secret.aws_access_key_id;
        const secretKey = secret.aws_secret_access_key;
        const sessionToken = secret.aws_session_token;

        const credentials = new AWS.Credentials(accessKey, secretKey, sessionToken);

        const cloudwatch = new AWS.CloudWatch({ credentials: credentials, region: metricsInfo.region });

        var newStartDate = new Date(metricsInfo.startDate);
        newStartDate.setMinutes(metricsInfo.startDate.getMinutes() - 2);

        const params = {
            Namespace: "AWS/RDS",
            MetricName: metricsInfo.metricName,
            Dimensions: [
                {
                    Name: "DBInstanceIdentifier",
                    Value: metricsInfo.currDbId
                }
            ],
            StartTime: newStartDate,
            Period: metricsInfo.minutes * 60, 
            Statistics: metricsInfo.dataStatistics 
        };

        const metrics = await cloudwatch.getMetricStatistics(params).promise();

        return metrics;
    } catch (error) {
        throw new Error("Could not get metrics:", error);
    }
}

const startMetricsPolling = async (ruleParams, targetParams, secretName, userRegion) => {
    const keyInfo = await secretsManager.getSecretValue({ SecretId: secretName }).promise();

    let secret;
    if (keyInfo.SecretString) {
        secret = JSON.parse(keyInfo.SecretString);
    } else {
        secret = JSON.parse(Buffer.from(keyInfo.SecretBinary, 'base64').toString('utf-8'));
    }

    const accessKey = secret.aws_access_key_id;
    const secretKey = secret.aws_secret_access_key;
    const sessionToken = secret.aws_session_token;

    const credentials = new AWS.Credentials(accessKey, secretKey, sessionToken);

    const eventBridge = new AWS.EventBridge({ credentials: credentials, region: userRegion });

    try {
        await eventBridge.putRule(ruleParams).promise();

        await eventBridge.putTargets(targetParams).promise();
    } catch (error) {
        throw new Error("Error setting up scheduled event:", error);
    }
};

const stopMetricsPolling = async (ruleName, secretName, targetIds) => {
    try {
        const keyInfo = await secretsManager.getSecretValue({ SecretId: secretName }).promise();

        let secret;
        if (keyInfo.SecretString) {
            secret = JSON.parse(keyInfo.SecretString);
        } else {
            secret = JSON.parse(Buffer.from(keyInfo.SecretBinary, 'base64').toString('utf-8'));
        }

        const accessKey = secret.aws_access_key_id;
        const secretKey = secret.aws_secret_access_key;
        const sessionToken = secret.aws_session_token;

        const credentials = new AWS.Credentials(accessKey, secretKey, sessionToken);

        const eventBridge = new AWS.EventBridge({ credentials: credentials, region: userRegion });

        const targetParams = { Rule: ruleName, Ids: targetIds };

        await eventBridge.removeTargets(targetParams).promise();
        await eventBridge.deleteRule({ Name: ruleName }).promise();
    } catch (error) {
        throw new Error("Error setting up scheduled event:", error);
    }
    
}