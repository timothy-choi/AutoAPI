const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const ProjectBilling = sequelize.define('ProjectBilling',  {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ProjectId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    ProjectManagementId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    GroupProject: {
        type: DataTypes.BOOLEAN,
        allowNull: false,
    },
    GroupUsers: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
    },
    ProjectUser: {
        type: DataTypes.JSONB,
        allowNull: true,
    },
    GroupPaymentType: { //split round robin, pay split based on usage, or someone pays entire bill
        type: DataTypes.STRING,
        allowNull: true,
    },
    CurrentBill: {
        type: DataTypes.JSONB,
        allowNull: true,
    },
    ProjectBillingHistory: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
    },
    NextBillingDate: {
        type: DataTypes.DATE,
        allowNull: true
    },
    CurrentBillingPayment: {
        type: DataTypes.JSONB,
        allowNull: true,
    },
    ServiceUsageReportInfo: {
        type: DataTypes.ARRAY(DataTypes.JSONB),
        allowNull: true,
    }
});

module.exports = ProjectBilling;