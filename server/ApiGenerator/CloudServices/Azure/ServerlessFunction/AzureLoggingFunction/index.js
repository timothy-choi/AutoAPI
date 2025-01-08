module.exports = async (context, eventGridEvent, documents) => {
    try {
        if (eventGridEvent) {
            const logEntryData = eventGridEvent.data;

            var logEntryRecord = {
                "database": logEntryData.resourceType,
                "logEntryContent": logEntryData
            };

            context.res = {
                status: 200,
                body: JSON.stringify(logEntryRecord)
            };
        } else if (documents && documents.length > 0) {
            var logEntryRecord = {
                "database": "CosmosDB",
                "logEntryContent": documents[0]
            };

            context.res = {
                status: 200,
                body: JSON.stringify(logEntryRecord)
            };
        } else {
            context.res = {
                status: 404,
                body: JSON.stringify({
                    "message": "No logs found"
                })
            };
        }
    } catch (error) {
        context.res = {
            status: 500,
            body: error.message
        };
    }
};