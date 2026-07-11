const { Logging } = require('@google-cloud/logging');
const { PubSub } = require('@google-cloud/pubsub');
const { exec } = require('child_process');

exports.setUpPubSubTopic = async (projectId, topicName, serviceAccount) => {
    try {
        const pubSub = new PubSub({ projectId });

        const [topic] = await pubSub.createTopic(topicName);

        await topic.iam.setPolicy({
            bindings: [
              {
                role: 'roles/pubsub.publisher',
                members: [`serviceAccount:${serviceAccount}`],
              },
            ],
        });

        return topic;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.createLoggingSink = async (projectId, topicName, sinkName) => {
    try {
        const logging = new Logging({ projectId: projectId });

        const sinkDestination = `pubsub.googleapis.com/projects/${projectId}/topics/${topicName}`;
   
        var [sink, logResponse] = await logging.sink(sinkName).create({
            destination: sinkDestination,
            filter: `logName="projects/${PROJECT_ID}/logs/${logName}"`, 
        });

        return [sink, logResponse];
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.deployCloudFunction = async (functionName, functionRuntime, topicName, region, functionEntryPoint, sourceDir) => {
    try {
        exec(
            `gcloud functions deploy ${functionName} ` +
              `--runtime ${functionRuntime} ` +
              `--source ${sourceDir} ` + 
              `--trigger-topic ${topicName} ` +
              `--entry-point ${functionEntryPoint} ` +
              `--region ${region}`,
            (error) => {
              if (error) {
                throw new Error(`Error deploying Cloud Function: ${error.message}`);
              }
            }
        );
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.createSchedulerJob = async (projectId, location, schedule, timezone, schedulerJobName) => {
    try {
        const schedulerClient = new CloudSchedulerClient();

        const parent = schedulerClient.locationPath(projectId, location);

        const pubSubTarget = {
            topicName: `projects/${projectId}/topics/${topicName}`,
            data: Buffer.from(JSON.stringify({ trigger: 'scheduled' })),
        };

        const job = {
            name: `projects/${projectId}/locations/${location}/jobs/${schedulerJobName}`,
            pubsubTarget: pubSubTarget,
            schedule: schedule,
            timeZone: timezone,
        };

        const [response] = await schedulerClient.createJob({ parent, job });

        return response;
    } catch (error) {
        throw new Error(error.message);
    }
};