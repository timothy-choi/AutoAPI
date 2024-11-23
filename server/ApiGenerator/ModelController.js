const ModelService = require('./ModelService');

exports.GetModelById = async (req, res) => {
    try {
        var model = await ModelService.GetModelById(req.modelId);

        return res.status(200).body(model);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetModelByName = async (req, res) => {
    try {
        var model = await ModelService.GetModelByName(req.modelName);

        return res.status(200).body(model);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.CreateModel = async (req, res) => {
    try {
        var model = await ModelService.GetModelById(req.body.name);

        if (model) {
            return res.status(500).json({ error: 'User already exists'});
        }

        var modelInfo = await ModelService.CreateModel(req.body);

        return res.status(201).json(modelInfo);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditVersion = async (req, res) => {
    try {
        await ModelService.EditVersion(req.modelId, req.version);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddModelAttribute = async (req, res) => {
    try {
        await ModelService.AddModelAttribute(req.modelId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteModelAttribute = async (req, res) => {
    try {
        await ModelService.DeleteModelAttribute(req.modelId, req.modeAttributeId, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditModelAttribute = async (req, res) => {
    try {
        await ModelService.EditModelAttribute(req.modelId, req.modeAttributeId, req.body, req.username);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditModelDescription = async (req, res) => {
    try {
        await ModelService.EditModelDescription(req.modelId, req.body.modelDesc);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteModel = async (req, res) => {
    try {
        var model = await ModelService.GetModelById(req.modelId);

        if (!model) {
            return res.status(500).json({ error: 'Model does not exist'});
        }

        await ModelService.DeleteModel(req.modelId);

        return res.status(201).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}



