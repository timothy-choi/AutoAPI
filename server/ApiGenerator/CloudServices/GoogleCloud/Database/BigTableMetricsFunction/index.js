const { Monitoring } = require('@google-cloud/monitoring');
const monitoring = new Monitoring.MetricServiceClient();

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

const getMetricsByMetricType = async (projectId, metricType, interval, intervalMinutes) => {
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

    } catch (error) {
        
    }
};