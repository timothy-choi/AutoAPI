const AWS = require('aws-sdk');
const secretsManager = new AWS.SecretsManager();

exports.handler = async (event) => {
    if (event.source === "aws.rds") {
        if (event.dbInfo["status"] === "available") {
            await startMetricsPolling(event.dbInfo["ruleParams"], event.dbInfo["targetParams"], event.userInfo["secretName"], event.userInfo["userRegion"]);
        } else if (event.dbInfo["status"] === "stopped") {
            await stopMetricsPolling(event.metricsInfo["ruleName"], event.userInfo["secretName"], event.metricsInfo["targetIds"]);
        }
    } else if (event.action === "collectMetrics") {
        var metricsData = await getRDSMetrics(event.metricsInfo, event.userInfo["secretName"]);

        var events = await getRDSEvents(event.metricsInfo.currDbId);

        var instanceHealthStatusReport = await assessInstanceHealth(metricsData.DataMatrics, events);

        var allMetricsInfoResponse = {
            MetricsDataInfo: metricsData.DataMetrics,
            MetricsStatsInfo: metricsData.DataMetricsStatsList,
            HealthStatusReport: instanceHealthStatusReport
        };

        return { statusCode: 200, body: JSON.stringify(allMetricsInfoResponse)};
    } else {
        return { statusCode: 400, body: "Unhandled event type" };
    }

    return { statusCode: 200, body: "Success" };
};

const getRDSEvents = async (dbInstanceId) => {
    const params = {
        SourceIdentifier: dbInstanceId,
        SourceType: 'db-instance',
        StartTime: new Date(Date.now() - 24 * 60 * 60 * 1000), 
        MaxRecords: 200
    };

    const response = await rds.describeEvents(params).promise();

    return response.Events.map(event => ({
        message: event.Message,
        date: event.Date
    }));
};

const assessInstanceHealth = async (metrics, events) => {
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

    events.forEach(event => {
        if (event.message.includes('failed') || event.message.includes('error')) {
            healthStatus.status = 'critical';
            healthStatus.issues.push(`Event issue: ${event.message}`);
        }
    });

    return healthStatus;
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

        var metrics_stats_list = [];

        for (let i = 0; i < metricsInfo.metricNames.length; ++i) {
            const params = {
                Namespace: "AWS/RDS",
                MetricName: metricsInfo.metricNames[i],
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
                        Namespace: "AWS/RDS",
                        MetricName: metricsInfo.metricNames[j],
                        Dimensions: [
                            {
                                Name: "DBInstanceIdentifier",
                                Value: metricsInfo.currDbId
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


        completeMetricsDataInfo = completeMetricsDataInfo.Metrics.reduce((acc, result) => {
            acc[result.Id] = result.Values[0] || 0;
            return acc;
        }, {});

        return { DataMetrics: completeMetricsDataInfo, DataMetricsStatsList: metrics_stats_list };
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