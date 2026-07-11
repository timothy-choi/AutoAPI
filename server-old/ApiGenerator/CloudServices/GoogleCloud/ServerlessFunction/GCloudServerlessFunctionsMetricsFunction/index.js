const { Monitoring } = require('@google-cloud/monitoring');
const { GoogleAuth } = require('google-auth-library');
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

const getMetricsByMetricType = async (metricType, interval, projectName, client) => {
    try {
        const filter = `metric.type = "${metricType}"`;
        const [timeSeries] = await client.listTimeSeries({
          name: projectName,
          filter,
          interval,
        });

        return timeSeries;
    } catch (error) {
        throw new Error(error.message);
    }
};


exports.monitorCloudSQLMetrics = async (req, res) => {
    try {
        var authClient = await createOAuth2Client(req.body.accessToken, req.body.refreshToken);

        const client = new Monitoring.MetricServiceClient({ authClient });

        var allMetrics = [];

        var interval = {
            startTime: { seconds: Date.now() / 1000 - (60 * req.body.minutes) }, 
            endTime: { seconds: Date.now() / 1000 }, 
        };

        for (let i = 0; i < req.body.metricTypes.length; ++i) {
            var metricData = await getMetricsByMetricType(req.body.metricTypes[i], interval, req.body.projectName, client);

            allMetrics.push({ metricType: req.body.metricTypes[i], data: metricData, });
        }

        if (req.body.autoTrigger) {
            await publishMetricsData(JSON.stringify(allMetrics));
        }

        return res.status(201).send({'metricResults': allMetrics});
    } catch (error) {
        return res.status(500).send(`Error fetching metrics: ${error.message}`);
    }
};