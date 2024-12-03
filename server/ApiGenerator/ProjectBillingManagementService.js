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

exports.AddGroupUser = async (billingId, groupUser) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance.GroupUsers.push(groupUser);

    await billingManagmentInstance.save();
}

exports.RemoveGroupUser = async (billingId, groupUserId) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance.GroupUsers.filter(billingInstance => billingInstance.id != groupUserId);

    await billingManagmentInstance.save();
}

exports.EditGroupUser = async (billingId, groupUserId, updatedGroupUser) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    var index = billingManagmentInstance.GroupUsers.findIndex(groupUser = groupUser.id != groupUserId);

    billingManagmentInstance.GroupUsers.splice(index, 1);

    billingManagmentInstance.GroupUsers.splice(index, 0, updatedGroupUser);

    await billingManagmentInstance.save();
}

exports.ModifyProjectUser = async (billingId, updatedProjectUserInfo) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance.ProjectUser = updatedProjectUserInfo;

    await billingManagmentInstance.save();
}

exports.SetGroupPaymentType = async (billingId, groupPaymentType) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance.GroupPaymentType = groupPaymentType;

    await billingManagmentInstance.save();
}

exports.SetCurrentBill = async (billingId, currentBill) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance.CurrentBill = currentBill;

    await billingManagmentInstance.save();
}

exports.AddBillingHistoryEntry = async (billingId, billingHistoryEntry) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance.ProjectBillingHistory.push(billingHistoryEntry);

    await billingManagmentInstance.save();
}

exports.SetNextBillingDate = async (billingId, nextBillingDate) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance.NextBillingDate = nextBillingDate;

    await billingManagmentInstance.save();
}

exports.SetCurrentBillingPayment = async (billingId, currentBillingPayment) => {
    var billingManagmentInstance = await this.GetBillingManagementById(billingId);

    if (billingManagmentInstance) {
        throw new Error('Instance already exists');
    }

    billingManagmentInstance.CurrentBillingPayment = currentBillingPayment;

    await billingManagmentInstance.save();
}

exports.DeleteBillingManagementInfo = async (billingId) => {
    var billingManagmentInstance = await GetBillingManagementById(billingId);

    if (!billingManagmentInstance) {
        throw new Error('Instance does not exist');
    }

    await billingManagmentInstance.destroy();
}