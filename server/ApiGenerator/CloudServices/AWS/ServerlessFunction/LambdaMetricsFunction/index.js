const AWS = require('aws-sdk');

const getLambdaMetrics = async (cloudwatch, metricName, minutes, statsOptions, dimensions) => {
    try {
        const params = {
          MetricName: metricName, 
          Namespace: 'Lambda',         
          Period: minutes * 60,                  
          StartTime: new Date(Date.now() - minutes * 60 * 1000).toISOString(), 
          EndTime: new Date().toISOString(),                             
          Statistics: statsOptions,      
          Dimensions: dimensions,
        };
    
        const data = await cloudwatch.getMetricStatistics(params).promise();

        return { statusCode: 200, body: JSON.stringify(data) };
    } catch (error) {
        return { statusCode: 500, body: JSON.stringify(error) };
    }
};