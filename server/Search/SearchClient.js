const { Client } = require('@elastic/elasticsearch');

const client = new Client({ node: '' }); 

module.exports = client;