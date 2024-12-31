const zlib = require('zlib');

exports.handler = async (event) => {
    try {
        const payload = Buffer.from(event.awslogs.data, 'base64');
        const uncompressedPayload = zlib.gunzipSync(payload).toString('utf8');
        const parsedPayload = JSON.parse(uncompressedPayload);

        const logEntries = parsedPayload.logEvents;

        var mostRecentLog = null;

        if (logEntries.length > 0) {
            mostRecentLog = logEntries[logEntries.length - 1];
        } else {
            return {
                statusCode: 404,
                body: JSON.stringify('No logs found')
            };
        }

        return {
            statusCode: 200,
            body: JSON.stringify(mostRecentLog)
        };

    } catch (error) {
        return {
            statusCode: 500,
            body: JSON.stringify('Error processing log:', error)
        };
    }
};