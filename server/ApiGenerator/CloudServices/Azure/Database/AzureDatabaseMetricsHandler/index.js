const { DefaultAzureCredential } = require("@azure/identity");
const { MonitorManagementClient } = require("@azure/arm-monitor");

const getDBInstanceMetrics = async (resourceId, subscriptionId, metricName, statistics) => {
    try {
        const credential = new DefaultAzureCredential();
        const monitorClient = new MonitorManagementClient(credential, subscriptionId);

        const now = new Date();
        const startTime = new Date(now.getTime() - (intervalInMinutes * 60 * 1000)).toISOString();  // Start time: 3 minutes ago
        const endTime = now.toISOString(); 

        const metricsResponse = await monitorClient.metrics.list(resourceId, {
            timespan: `${startTime}/${endTime}`,
            interval: `PT3M`,  
            metricnames: metricName,
            aggregation: statistics,
        });

        return metricsResponse;
    } catch (error) {
        throw new Error(error.message);
    }
}

const organizeMetricsData = (metricsData) => {
    const metricsMap = new Map();

    const metrics = metricsData.value || [];

    metrics.forEach((metric) => {
        const metricName = metric.name.value;

        if (!metricsMap.has(metricName)) {
            metricsMap.set(metricName, []);
        }

        metric.timeseries.forEach((ts) => {
            ts.data.forEach((point) => {
                const metricData = {
                    timeStamp: point.timeStamp,
                    average: point.average !== null && point.average !== undefined ? point.average : 0, 
                    minimum: point.minimum !== null && point.minimum !== undefined ? point.minimum : 0, 
                    maximum: point.maximum !== null && point.maximum !== undefined ? point.maximum : 0, 
                    total: point.total !== null && point.total !== undefined ? point.total : 0, 
                };

                metricsMap.get(metricName).push(metricData);
            });
        });

        return metrics;
    });
}

module.exports = async function (context, req) {
    var allMetricsResults = [];

    if (req.body.stop) {
        context.res = {
            status: 200,
            body: {
                message: "Metrics Handler to shut down"
            },
            headers: {
                "Content-Type": "application/json"
            }
        };

        return;
    }

    try {
        for (let i = 0; i < req.body.metricNames; ++i) {
            var metricsResponse = await getDBInstanceMetrics(req.body.resourceId, req.body.subscriptionId, req.body.metricNames[i], req.body.statistics);

            var metricsData = organizeMetricsData(metricsResponse);

            allMetricsResults.push(metricsData);
        }

        context.res = {
            status: 200,
            body: {
                metricsResults: allMetricsResults
            },
            headers: {
                "Content-Type": "application/json"
            }
        };
    } catch (error) {
        throw new Error(error.message);
    }
}