const { DefaultAzureCredential } = require('@azure/identity');
const { MetricsQueryClient } = require('@azure/monitor-query');
const amqp = require('amqplib');

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
}

const getCosmosDBMetrics = async (subscriptionId, resourceGroup, accountName, minuteDuration, metricStatistics, metricList) => {
    try {
        const credential = new DefaultAzureCredential();
        const metricsClient = new MetricsQueryClient(credential, subscriptionId);

        const resourceUri = `/subscriptions/${subscriptionId}/resourceGroups/${resourceGroup}/providers/Microsoft.DocumentDB/databaseAccounts/${accountName}`;

        const metricsResponse = await metricsClient.queryResource(resourceUri, {
            metricNames: metricList,
            timespan: `PT${minuteDuration}M`, 
            aggregations: metricStatistics,
        });

        return metricsResponse;
    } catch (error) {
        throw new Error(error.message);
    }
};

const organizeMetricsResults = async (metricsResponse) => {
    try {
        const metricsData = {};
        metricsResponse.metrics.forEach((metric) => {
            const metricName = metric.name;
            const dataPoints = [];

            metric.timeseries.forEach((series) => {
                series.data.forEach((dataPoint) => {
                    dataPoints.push({
                        timestamp: dataPoint.timestamp,
                        value: dataPoint.total,
                    });
                });
            });

            metricsData[metricName] = dataPoints;
        });

        return metricsData;
    } catch (error) {
        throw new Error(error.message);
    }
};

module.exports = async (context, req) => {
    try {
        var metricsResponse = await getCosmosDBMetrics(req.body.subscriptionId, req.body.resourceGroup, req.body.accountName, req.body.minuteDuration, req.body.metricStatistics, req.body.metricList);

        var processedMetricsResults = await organizeMetricsResults(metricsResponse);

        if (req.body.autoTrigger) {
            await publishMetricsData(JSON.stringify(processedMetricsResults));
        }

        context.res = {
            status: 200,
            body: {
                metricsResults: processedMetricsResults
            },
            headers: {
                "Content-Type": "application/json"
            }
        };
    } catch (error) {
        throw new Error(error.message);
    }
};