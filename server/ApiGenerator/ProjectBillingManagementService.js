const ProjectBillingManagement = require('./ProjectBillingManagement');

exports.GetBillingManagementById = async (billingId) => {
    var billingInfo = await ProjectBillingManagement.findByPk(billingId);

    return billingInfo;
}

exports.GetBillingManagementByProjectId = async (projectId) => {
    var billingInfo = await ProjectBillingManagement.findOne({ where: { ProjectId: projectId }});

    return billingInfo;
}

exports.CreateBillingManagementInfo = async (billingInfo) => {
    var billingManagmentInstance = await GetBillingManagementByProjectId(billingInfo.projectId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance = await ProjectBillingManagement.Create({
        ProjectId: billingInfo.projectId,
        ProjectManagementId: billingInfo.projectManagementId,
        GroupProject: billingInfo.groupProject,
        Currency: billingInfo.currency,
        PaymentPlatform: billingInfo.PaymentPlatform,
        ManualServiceControl: billingInfo.manualServiceControl
    });

    return billingManagmentInstance;
}

exports.DeleteBillingManagementInfo = async (billingId) => {
    var billingManagmentInstance = await GetBillingManagementById(billingId);

    if (!billingManagmentInstance) {
        throw new Error('Instance does not exist');
    }

    await billingManagmentInstance.destroy();
}