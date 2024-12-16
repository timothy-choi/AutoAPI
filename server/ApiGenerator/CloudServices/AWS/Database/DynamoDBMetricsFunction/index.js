const AWS = require('aws-sdk');
const secretsManager = new AWS.SecretsManager();

const getDynamoDBEvents = async (tableName, secretName, userRegion) => {
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

    const userCredentials = new AWS.Credentials(accessKey, secretKey, sessionToken);

    const dynamoDB = new AWS.DynamoDB({
        accessKeyId: userCredentials.accessKey,
        secretAccessKey: userCredentials.secretKey,
        sessionToken: userCredentials.sessionToken,
        region: userRegion
    });

    const params = {
        SourceIdentifier: tableName,
        SourceType: 'dynamodb-table',
        StartTime: new Date(Date.now() - 24 * 60 * 60 * 1000), 
        MaxRecords: 200
    };

    const response = await dynamoDB.describeTable(params).promise();

    return response.Table.Events.map(event => ({
        message: event.Message,
        date: event.Date
    }));
}

const getDynamoDBMetrics = async (metricsInfo, secretName) => {
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

        var metrics_stats_list = [];

        for (let i = 0; i < metricsInfo.metricNames.length; ++i) {
            const params = {
                Namespace: "AWS/DynamoDB",
                MetricName: metricsInfo.metricNames[i],
                Dimensions: [
                    {
                        Name: "TableName",
                        Value: metricsInfo.currTableName
                    }
                ],
                StartTime: newStartDate,
                Period: metricsInfo.minutes * 60, 
                Statistics: metricsInfo.dataStatistics 
            };

            const metricStatistics = await cloudwatch.getMetricStatistics(params).promise();

            metrics_stats_list.push(metricStatistics);
        }

        const dataMetricsRequest = {
            StartTime: newStartDate.toISOString(),
            EndTime: new Date().toISOString(),
            Metrics: []
        };

        for (let j = 0; j < metricsInfo.metricNames.length; j++) {
            const params = {
                Id: Math.random().toString(6),
                MetricStat: {
                    Metric: {
                        Namespace: "AWS/DynamoDB",
                        MetricName: metricsInfo.metricNames[j],
                        Dimensions: [
                            {
                                Name: "TableName",
                                Value: metricsInfo.currTableName
                            }
                        ]
                    },
                    Period: 300,
                    Stat: metricsInfo.dataStatistics 
                }
            };

            dataMetricsRequest.Metrics.push(params);
        }

        const completeMetricsDataInfo = await cloudwatch.getMetricData(dataMetricsRequest).promise();

        const metricsData = completeMetricsDataInfo.Metrics.reduce((acc, result) => {
            acc[result.Id] = result.Values[0] || 0;
            return acc;
        }, {});

        return { DataMetrics: metricsData, DataMetricsStatsList: metrics_stats_list };
    } catch (error) {
        throw new Error("Could not get metrics:", error);
    }
}

const assessTableHealth = async (metrics, events) => {
    const healthStatus = {
        status: 'healthy',
        issues: []
    };

    if (metrics.ReadThrottleEvents > 0) {
        healthStatus.status = 'warning';
        healthStatus.issues.push('Warning: Read throttling detected');
    }
    if (metrics.WriteThrottleEvents > 0) {
        healthStatus.status = 'warning';
        healthStatus.issues.push('Warning: Write throttling detected');
    }
    if (metrics.Latency > 100) {
        healthStatus.status = 'warning';
        healthStatus.issues.push('Warning: High latency detected');
    }

    //add more

    events.forEach(event => {
        if (event.message.includes('error') || event.message.includes('throttling')) {
            healthStatus.status = 'critical';
            healthStatus.issues.push(`Event issue: ${event.message}`);
        }
    });

    return healthStatus;
};

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
        throw new Error("Error stopping scheduled event:", error);
    }
};

exports.handler = async (event) => {
    if (event.source === "aws.dynamodb") {
        if (event.tableInfo["status"] === "active") {
            await startMetricsPolling(event.tableInfo["ruleParams"], event.tableInfo["targetParams"], event.userInfo["secretName"], event.userInfo["userRegion"]);
        } else if (event.tableInfo["status"] === "inactive") {
            await stopMetricsPolling(event.metricsInfo["ruleName"], event.userInfo["secretName"], event.metricsInfo["targetIds"]);
        }
    } else if (event.action === "collectMetrics") {
        var metricsData = await getDynamoDBMetrics(event.metricsInfo, event.userInfo["secretName"]);

        var events = await getDynamoDBEvents(event.metricsInfo.currTableName, event.userInfo["secretName"], event.userInfo["userRegion"]);

        var tableHealthStatusReport = await assessTableHealth(metricsData.DataMetrics, events);

        var allMetricsInfoResponse = {
            MetricsDataInfo: metricsData.DataMetrics,
            MetricsStatsInfo: metricsData.DataMetricsStatsList,
            HealthStatusReport: tableHealthStatusReport
        };

        return { statusCode: 200, body: JSON.stringify(allMetricsInfoResponse) };
    } else {
        return { statusCode: 400, body: "Unhandled event type" };
    }

    return { statusCode: 200, body: "Success" };
};