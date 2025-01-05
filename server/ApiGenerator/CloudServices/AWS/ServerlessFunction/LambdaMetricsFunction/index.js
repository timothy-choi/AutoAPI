const AWS = require('aws-sdk');
const amqp = require('amqplib');
const secretsManager = new AWS.SecretsManager();

const getLambdaMetrics = async (metricsInfo, secretName) => {
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
                Namespace: "Lambda",
                MetricName: metricsInfo.metricNames[i],
                Dimensions: [
                    {
                        Name: "LambdaIdentifier",
                        Value: metricsInfo.currFunctionId
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
                        Namespace: "Lambda",
                        MetricName: metricsInfo.metricNames[j],
                        Dimensions: [
                            {
                                Name: "LambdaIdentifier",
                                Value: metricsInfo.currFunctionId
                            }
                        ]
                    },
                    Period: metricsInfo.minutes * 60,
                    Stat: metricsInfo.dataStatistics 
                }
            };

            dataMetricsRequest.Metrics.push(params);
        }

        const completeMetricsDataInfo = await cloudwatch.getMetricData(dataMetricsRequest).promise();


        completeMetricsDataInfo = completeMetricsDataInfo.Metrics.reduce((acc, result) => {
            acc[result.Id] = result.Values[0] || 0;
            return acc;
        }, {});

        return { DataMetrics: completeMetricsDataInfo, DataMetricsStatsList: metrics_stats_list };
    } catch (error) {
        throw new Error("Could not get metrics:", error);
    }
};

const assessInstanceHealth = async (metrics) => {
    const healthStatus = {
        status: 'healthy',
        issues: []
    };

    if (metrics.cpuUtilization > 75) {
        healthStatus.status = 'warning';
        healthStatus.issues.push('Warning: High CPU utilization');
    }
    if (metrics.cpuUtilization > 90) {
        healthStatus.status = 'critical';
        healthStatus.issues.push('Warning: Critical CPU utilization');
    }
    if (metrics.freeStorageSpace < 10 * 1024 * 1024 * 1024) { 
        healthStatus.status = 'warning';
        healthStatus.issues.push('Warning: Low free storage space');
    }
    if (metrics.freeStorageSpace < 1 * 1024 * 1024 * 1024) {
        healthStatus.status = 'critical';
        healthStatus.issues.push('Warning: Critically low free storage space');
    }

    //add more warning checks to other metrics

    return healthStatus;
};

const publishMetricsData = async (metricsData) => {
    try {
        const connection = await amqp.connect('');  
        const channel = await connection.createChannel();

        channel.assertQueue("", { durable: true });
        channel.sendToQueue("", Buffer.from(metricsData), { persistent: true });

        await channel.close();
        await connection.close();
    } catch (error) {
        throw new Error(error.message);
    }
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
        throw new Error("Error setting up scheduled event:", error);
    }
};

exports.handler = async (event) => {
    if (event.source === "aws.lambda") {
        if (event.lambdaInfo["status"] === "available") {
            await startMetricsPolling(event.dbInfo["ruleParams"], event.dbInfo["targetParams"], event.userInfo["secretName"], event.userInfo["userRegion"]);
        } else if (event.lambdaInfo["status"] === "stopped") {
            await stopMetricsPolling(event.metricsInfo["ruleName"], event.userInfo["secretName"], event.metricsInfo["targetIds"]);
        }
    } else if (event.action === "collectMetrics") {
        var metricsData = await getLambdaMetrics(event.metricsInfo, event.userInfo["secretName"]);

        var instanceHealthStatusReport = await assessInstanceHealth(metricsData.DataMetrics);

        var allMetricsInfoResponse = {
            MetricsDataInfo: metricsData.DataMetrics,
            MetricsStatsInfo: metricsData.DataMetricsStatsList,
            HealthStatusReport: instanceHealthStatusReport
        };

        if (event.autoTrigger) {
            await publishMetricsData(JSON.stringify(allMetricsInfoResponse));
        }

        return { statusCode: 200, body: JSON.stringify(allMetricsInfoResponse)};
    } else {
        return { statusCode: 400, body: "Unhandled event type" };
    }

    return { statusCode: 500, body: "Error" };
};