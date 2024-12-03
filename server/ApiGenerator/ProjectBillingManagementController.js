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

exports.AddGroupUser = async (req, res) => {
    try {
        await BillingService.AddGroupUser(req.billingId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveGroupUser = async (req, res) => {
    try {
        await BillingService.RemoveGroupUser(req.billingId, req.groupUserId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditGroupUser = async (req, res) => {
    try {
        await BillingService.EditGroupUser(req.billingId, req.groupUserId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.ModifyProjectUser = async (req, res) => {
    try {
        await BillingService.ModifyProjectUser(req.billingId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.ModifyGroupPaymentType = async (req, res) => {
    try {
        await BillingService.SetGroupPaymentType(req.billingId, req.groupPaymentType);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetCurrentBill = async (req, res) => {
    try {
        await BillingService.SetCurrentBill(req.billingId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddBillingHistoryEntry = async (req, res) => {
    try {
        await BillingService.AddBillingHistoryEntry(req.billingId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetNextBillingDate = async (req, res) => {
    try {
        await BillingService.SetNextBillingDate(req.billingId, req.billingDate);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetCurrentBillingPayment = async (req, res) => {
    try {
        await BillingService.SetCurrentBillingPayment(req.billingId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddServiceUsageReportInfo = async (req, res) => {
    try {
        await BillingService.AddServiceUsageReportInfoEntry(req.billingId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveServiceUsageReportInfo = async (req, res) => {
    try {
        await BillingService.RemoveServiceUsageReportInfoEntry(req.billingId, req.serviceUsageReportInfoId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditServiceUsageReportInfo = async (req, res) => {
    try {
        await BillingService.EditServiceUsageReportInfoEntry(req.billingId, req.serviceUsageReportInfoId, req.body);

        return res.status(200).json(null);
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