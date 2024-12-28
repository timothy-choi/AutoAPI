const { Monitoring } = require('@google-cloud/monitoring');
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
};

const createOAuth2Client = async (accessToken, refreshToken) => {
    try {
        const oauth2Client = new GoogleAuth().fromJSON({
            type: 'authorized_user',
            client_id: '',
            client_secret: '',
            refresh_token: refreshToken,
        });

        oauth2Client.credentials = { access_token: accessToken };

        return oauth2Client;
    } catch (error) {
        throw new Error(error.message);
    }
};

const getMetricsByMetricType = async (monitoring, projectId, metricType, interval, intervalMinutes) => {
    try {
        const request = {
            name: `projects/${projectId}`,
            filter: `metric.type="${metricType}"`,
            interval: interval,
            aggregation: {
                alignmentPeriod: { seconds: intervalMinutes * 60 },
                perSeriesAligner: 'ALIGN_MEAN',
            },
        };

        const [timeSeries] = await monitoring.listTimeSeries(request);

        if (timeSeries.length === 0) {
            return null;
        }

        const metricsData = {};

        timeSeries.forEach((series) => {
            const { metric, resource, points } = series;
            const resourceId = resource.labels.instance_id || resource.labels.cluster_id;

            if (!metricsData[metric.type]) {
                metricsData[metric.type] = [];
            }

            metricsData[metric.type].push({
                resourceId,
                dataPoints: points.map((point) => ({
                    value: point.value.doubleValue,
                    time: point.interval.endTime,
                })),
            });
        });

        return metricsData;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.monitorBigTableMetrics = async (req, res) => {
    try {
        var oauthClient = await createOAuth2Client(req.body.accessToken, req.body.refreshToken);

        const client = new Monitoring.MetricServiceClient({ oauthClient });

        var allMetrics = [];

        const now = Date.now();
        const startTime = new Date(now - req.body.intervalMinutes * 60 * 1000); // X minutes ago
        const endTime = new Date(now);

        var interval = {
            startTime: { seconds: startTime.getTime() / 1000 },
            endTime: { seconds: endTime.getTime() / 1000 },
        };

        for (let i = 0; i < req.body.metricTypes.length; ++i) {
            var metricsData = await getMetricsByMetricType(client, req.body.projectId, req.body.metricTypes[i], interval, req.body.intervalMinutes);

            if (metricsData == null) {
                allMetrics.push({metricType: req.body.metricTypes[i], data: null});

                continue;
            }

            allMetrics.push({ metricType: req.body.metricTypes[i], data: metricsData });
        }

        if (req.body.autoTrigger) {
            await publishMetricsData(JSON.stringify(allMetrics));
        }

        return res.status(201).send({"metricsData": allMetrics});
    } catch (error) {
        return res.status(500).send(error.message);
    }
};