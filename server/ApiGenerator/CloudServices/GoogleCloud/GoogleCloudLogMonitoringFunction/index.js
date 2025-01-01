const { Logging } = require('@google-cloud/logging');

exports.logListener = async (event, context) => {
    try {
        const logging = new Logging();

        const eventData = event.data ? Buffer.from(event.data, 'base64').toString() : null;
        const payload = eventData ? JSON.parse(eventData) : {};

        const logName = payload.logName;

        const [entries] = await logging.getEntries({
            filter: `logName="${logName}"`,
            orderBy: 'timestamp desc',
            pageSize: 1,
        });

        if (entries.length > 0) {
            const mostRecentLog = entries[0];

            return {
                success: true,
                log: mostRecentLog,
            };
        } else {
            return {
              success: false,
              message: 'No log entries found.',
            };
        }
    } catch (error) {
        return {
            success: false,
            error: error.message,
        };
    }
};