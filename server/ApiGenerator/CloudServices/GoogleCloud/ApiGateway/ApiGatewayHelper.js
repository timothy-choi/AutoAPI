const axios = require('axios');

async function getApiConfigStatus(token, projectId, location, apiName, configId) {
    const url = `https://apigateway.googleapis.com/v1/projects/${projectId}/locations/${location}/apis/${apiName}/configs/${configId}`;

    try {
      const response = await axios.get(url, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      return response.data;
    } catch (error) {
      throw new Error(`Failed to fetch API config status: ${error.message}`);
    }
};

const retryOperation = async (operation, retries = 3, delay = 1000) => {
    let attempt = 0;
    while (attempt < retries) {
        try {
            return await operation();
        } catch (error) {
            if (attempt === retries - 1) {
                throw new Error(`Operation failed after ${retries} attempts: ${error.message}`);
            }
            attempt++;
            console.log(`Retrying operation... Attempt ${attempt + 1}`);
            await new Promise(resolve => setTimeout(resolve, delay));
        }
    }
};

const pollFunction = async (operationFn, checkStatusFn, maxAttempts = 10, delay = 5000) => {
    let attempt = 0;
    while (attempt < maxAttempts) {
        try {
            const status = await checkStatusFn();
            if (status === 'SUCCESS' || status === 'DONE') {
                return { success: true, message: 'Operation completed successfully' };
            } else if (status === 'FAILURE') {
                throw new Error('Operation failed');
            }

            console.log(`Polling attempt ${attempt + 1}: Status - ${status}`);
            attempt++;
            await new Promise(resolve => setTimeout(resolve, delay));
        } catch (error) {
            if (attempt === maxAttempts - 1) {
                throw new Error(`Polling failed after ${maxAttempts} attempts: ${error.message}`);
            }
            console.log(`Polling attempt ${attempt + 1} failed: ${error.message}`);
            attempt++;
        }
    }

    throw new Error(`Polling timed out after ${maxAttempts} attempts`);
};

exports.createApi = async (token, apiName, url, realApiName) => {
    try {
        const data = {
            apiId: apiName,
            displayName: realApiName,
        };
        
        const operation = async () => {
            const response = await axios.post(url, data, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
            });

            return response.data;
        };

        const operationResponse = await retryOperation(operation, 3, 2000);

        const checkStatusFn = async (operationName) => {
            const url = `https://apigateway.googleapis.com/v1/${operationName}`;
            const response = await axios.get(url, {
                headers: { Authorization: `Bearer ${token}` },
            });
    
            return response.data.done ? (response.data.error ? 'FAILURE' : 'SUCCESS') : 'PENDING';
        };

        const pollResult = await pollFunction(
            () => Promise.resolve(), 
            () => checkStatusFn(operationResponse.name), 
            3,
            5000
        );

        return pollResult;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.getApiInfo = async (token, projectId, apiId) => {
    try {
        const url = `https://apigateway.googleapis.com/v1/projects/${projectId}/locations/global/apis/${apiId}`;
        const response = await axios.get(url, {
            headers: { Authorization: `Bearer ${token}` },
        });

        return response.data;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.createApiConfig = async (token, apiName, configId, projectId, location, openApiSpecUrl, serviceAcct) => {
    try {
        const url = `https://apigateway.googleapis.com/v1/projects/${projectId}/locations/${location}/apis/${apiName}/configs`;

        const data = {
            apiConfigId: configId,
            displayName: `${apiName} Configuration`,
            openApiDocuments: [
            {
                path: openApiSpecUrl,
            },
            ],
            gatewayServiceAccount: serviceAcct,
        };

        var operation = async () => {
            const response = await axios.post(url, data, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
            });

            return response.data;
        }

        await retryOperation(operation, 3, 2000);

        return await pollFunction(
            () => getApiConfigStatus(token, projectId, location, apiName, configId),
            getApiConfigStatus,
            10,
            5000
        );
    } catch (error) {
        throw new Error(error.message);
    }
};