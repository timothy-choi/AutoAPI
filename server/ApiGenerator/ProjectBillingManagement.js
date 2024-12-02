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
    },
    Currency: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    PaymentPlatform: {
        type: DataTypes.ENUM('STRIPE', 'PAYPAL'),
        allowNull: true,
    },
    AutomaticPayment: {
        type: DataTypes.BOOLEAN,
        allowNull: true,
    },
    ManualServiceControl: { //indicates if owner of project wants to use their cloud account to create services there or not. If so, payment will be made toward owner's account
        type: DataTypes.BOOLEAN,
        allowNull: false,
    }
});

module.exports = ProjectBilling;