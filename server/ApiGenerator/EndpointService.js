const EndpointsDefinition = require('./Endpoints');

exports.GetEndpointsById = async (endpointId) => {
    var uuidVal = mongoose.types.ObjectId(endpointId);

    var endpoints = await EndpointsDefinition.findById(uuidVal);

    return endpoints;
}