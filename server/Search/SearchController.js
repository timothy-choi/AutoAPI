const { equal } = require('joi');
const client = require('./SearchClient');


exports.IndexDocument = async (req, res) => {
    try {
        const response = await client.index({
            index: req.index,
            body: req.body
        });

        if (response.statusCode != 201) {
            return res.status(500).json({ error: error.message });
        }

        return res.status(201).json(response);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}; 

exports.Search = async (req, res) => {
    try {
        const response = await client.search({
            index: req.index,
            body: {
                query: {
                    match: {
                        content: req.query.searchTerm 
                    }
                }
            }
        });

        if (response.statusCode != 200) {
            return res.status(500).json({ error: error.message });
        }

        return res.status(200).json(response.body.hits.hits);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
};

exports.UpdateDocument = async (req, res) => {
    try {
        const response = await client.update({
            index: req.index,
            id: req.id,
            body: {
                doc: req.body
            }
        });

        if (response.statusCode != 200) {
            return res.status(500).json({ error: error.message });
        }

        return res.status(200).json(response);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }   
};

exports.DeleteDocument = async (req, res) => {
    try {
        const response = await client.delete({
            index: req.index,
            id: req.id
        });

        if (response.statusCode != 200) {
            return res.status(500).json({ error: error.message });
        }

        return res.status(200).json(response);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }   
};

