const jwt = require('jsonwebtoken');

exports.AuthenticateToken = (req, res, next) => {
    const token = req.session.token || req.headers['authorization'];

    if (!token) {
       return res.status(401).json({ message: 'No token found' });
    }
    
    jwt.verify(token, process.env.JWT_SECRET, (err, decoded) => {
        if (err) {
            return res.status(403).json({ message: 'Invalid or expired token' });
        }

        req.userId = decoded.id;
        next();
    });
}