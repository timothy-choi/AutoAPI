const { DefaultAzureCredential } = require("@azure/identity");
const { MonitorClient } = require("@azure/arm-monitor");
const axios = require("axios");

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
            metricnames: metricName,
            aggregation: statistics,
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