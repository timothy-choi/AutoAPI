const { DefaultAzureCredential } = require("@azure/identity");
const { MonitorClient } = require("@azure/arm-monitor");
const amqp = require("amqplib");

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

modules.exports = async (context, req) => {
    try {
        const credential = new DefaultAzureCredential();
        const monitorClient = new MonitorClient(credential, req.body.subscriptionId);

        const now = new Date();
        const startTime = new Date(now.getTime() - (req.body.intervalInMinutes * 60 * 1000)).toISOString();  
        const endTime = now.toISOString(); 

        const result = await monitorClient.metrics.list(req.body.resourceId, {
            timespan: `${startTime}/${endTime}`,
            interval: req.body.interval,  
            metricnames: req.body.metrics,
        });

        const response = {
            message: "Metrics tracked successfully",
            metrics: []
        };

        result.value.forEach(metric => {
            const metricData = {
                name: metric.name.localizedValue,
                timeseries: metric.timeseries.map(series => {
                    return {
                        data: series.data.map(point => ({
                            timeStamp: point.timeStamp,
                            value: point[series.aggregation?.type]  
                        }))
                    };
                })
            };
            response.metrics.push(metricData);
        });

        if (req.body.autoTrigger) {
            await publishMetricsData(JSON.stringify(response));
        }

        context.res = {
            status: 200,
            body: response
        };
    } catch (error) {
        context.res = {
            status: 500,
            body: "Error fetching metrics from Azure Monitor: " + error.message
        };
    }   
};