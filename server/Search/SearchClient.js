const { Client } = require('@elastic/elasticsearch');

const client = new Client({ node: '' }); 

const exists = await client.indices.exists({ index: 'ApiUsers' });

if (!exists.body) {
    await client.indices.create({
        index: 'ApiUsers',
        body: {
          settings: {
            number_of_shards: 1,
            number_of_replicas: 1,
          },
          mappings: {
            properties: {
              title: { type: 'text' },
              content: { type: 'text' },
              date: { type: 'date' },
            },
          },
        },
      });
}

exists = await client.indices.exists({ index: 'ApiGroups' });

if (!exists.body) {
    await client.indices.create({
        index: 'ApiGroups',
        body: {
          settings: {
            number_of_shards: 1,
            number_of_replicas: 1,
          },
          mappings: {
            properties: {
              title: { type: 'text' },
              content: { type: 'text' },
              date: { type: 'date' },
            },
          },
        },
      });
}

exists = await client.indices.exists({ index: 'ApiProjects' });

if (!exists.body) {
    await client.indices.create({
        index: 'ApiProjects',
        body: {
          settings: {
            number_of_shards: 1,
            number_of_replicas: 1,
          },
          mappings: {
            properties: {
              title: { type: 'text' },
              content: { type: 'text' },
              date: { type: 'date' },
            },
          },
        },
      });
}

module.exports = client;