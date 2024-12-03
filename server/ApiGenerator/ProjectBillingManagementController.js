const BillingService = require('./ProjectBillingManagementService');

exports.GetBillingInfoById = async (req, res) => {
    try {
        var billingInfo = await BillingService.GetBillingManagementById(req.billingId);

        return res.status(200).body(billingInfo);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetBillingInfoByProjectId = async (req, res) => {
    try {
        var billingInfo = await BillingService.GetBillingManagementByProjectId(req.projectId);

        return res.status(200).body(billingInfo);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.CreateBillingInfo = async (req, res) => {
    try {
        const billingInfo = await BillingService.CreateBillingManagementInfo(req.body);

        return res.status(201).body(billingInfo);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteBillingInfo = async (req, res) => {
    try {
        await BillingService.DeleteBillingManagementInfo(req.billingId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}