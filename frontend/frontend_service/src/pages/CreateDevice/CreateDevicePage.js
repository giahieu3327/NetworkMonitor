import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { axiosClient } from '../../api/axiosClient';

import '../../styles/common.css';
import './CreateDevicePage.css';

const createDefaultCredential = (
  protocolType = 'SNMPV2C',
  credentialName = 'SNMP_Credential'
) => ({
  credentialName,
  credentialType: 'SNMP',
  protocolType,
  communityString: '',
  port: 161,
  isActive: true,
});

const validateOidConfigFile = (file) => {
  if (!file) {
    return 'Vui lòng chọn file OID Config!';
  }

  const extension = file.name.split('.').pop()?.toLowerCase();

  if (!['xlsx', 'xls', 'csv'].includes(extension)) {
    return 'Chỉ hỗ trợ file XLSX, XLS hoặc CSV!';
  }

  return '';
};

const DeviceInfoSection = ({ device, onChange, disabled }) => {
  const fields = [
    {
      name: 'deviceName',
      label: 'Tên thiết bị',
      placeholder: 'Ví dụ: Cisco C9300-24T',
    },
    {
      name: 'ipAddress',
      label: 'Địa chỉ IP',
      placeholder: '172.16.200.1',
    },
    {
      name: 'deviceType',
      label: 'Loại thiết bị',
      placeholder: 'Router, Switch...',
    },
  ];

  return (
    <section className="device-create-section">
      <div className="device-create-section-title">
        <span className="device-step-number">1</span>
        <div>
          <h3>Thông tin thiết bị</h3>
          <p>Nhập thông tin cơ bản của thiết bị mạng.</p>
        </div>
      </div>

      <div className="form-grid">
        {fields.map((field) => (
          <div
            className={`form-group ${
              field.name === 'deviceName' ? 'device-full-width' : ''
            }`}
            key={field.name}
          >
            <label htmlFor={field.name}>{field.label} *</label>
            <input
              id={field.name}
              name={field.name}
              value={device[field.name]}
              onChange={onChange}
              placeholder={field.placeholder}
              disabled={disabled}
              required
            />
          </div>
        ))}
      </div>
    </section>
  );
};

const CredentialSection = ({
  credentials,
  onChange,
  onAdd,
  onRemove,
  disabled,
}) => {
  const updateField = (index, field, value) => {
    onChange((prev) =>
      prev.map((item, i) =>
        i === index ? { ...item, [field]: value } : item
      )
    );
  };

  return (
    <section className="device-create-section">
      <div className="device-create-section-title">
        <span className="device-step-number">2</span>
        <div>
          <h3>Device Credentials</h3>
          <p>Thiết lập một hoặc nhiều thông tin xác thực SNMP.</p>
        </div>
      </div>

      {credentials.map((credential, index) => (
        <div className="device-credential-card" key={index}>
          <div className="device-credential-header">
            <div className="device-credential-heading">
              <strong>Credential {index + 1}</strong>
              <span className="credential-protocol-badge">
                {credential.protocolType}
              </span>
            </div>

            {credentials.length > 1 && (
              <button
                type="button"
                className="credential-remove-button"
                onClick={() => onRemove(index)}
                disabled={disabled}
              >
                Xóa
              </button>
            )}
          </div>

          <div className="form-grid">
            <div className="form-group">
              <label>Credential Name *</label>
              <input
                value={credential.credentialName}
                onChange={(e) =>
                  updateField(index, 'credentialName', e.target.value)
                }
                placeholder="SNMP_Cisco_V2"
                disabled={disabled}
                required
              />
            </div>

            <div className="form-group">
              <label>Credential Type *</label>
              <select
                className="role-select"
                value={credential.credentialType}
                onChange={(e) =>
                  updateField(index, 'credentialType', e.target.value)
                }
                disabled={disabled}
              >
                <option value="SNMP">SNMP</option>
              </select>
            </div>

            <div className="form-group">
              <label>Protocol Type *</label>
              <select
                className="role-select"
                value={credential.protocolType}
                onChange={(e) =>
                  updateField(index, 'protocolType', e.target.value)
                }
                disabled={disabled}
              >
                <option value="SNMPV1">SNMPV1</option>
                <option value="SNMPV2C">SNMPV2C</option>
              </select>
            </div>

            <div className="form-group">
              <label>Community String *</label>
              <input
                value={credential.communityString}
                onChange={(e) =>
                  updateField(index, 'communityString', e.target.value)
                }
                placeholder="Nhập Community String"
                disabled={disabled}
                required
              />
            </div>

            <div className="form-group">
              <label>Port *</label>
              <input
                type="number"
                min="1"
                max="65535"
                value={credential.port}
                onChange={(e) =>
                  updateField(index, 'port', e.target.value)
                }
                disabled={disabled}
                required
              />
            </div>

            <div className="form-group device-checkbox-group">
              <label>Is Active</label>
              <label className="checkbox-group">
                <input
                  type="checkbox"
                  checked={credential.isActive}
                  onChange={(e) =>
                    updateField(index, 'isActive', e.target.checked)
                  }
                  disabled={disabled}
                />
                <span>Cho phép sử dụng Credential</span>
              </label>
            </div>
          </div>
        </div>
      ))}

      <button
        type="button"
        className="credential-add-button"
        onClick={onAdd}
        disabled={disabled}
      >
        + Thêm Credential
      </button>
    </section>
  );
};

const OidConfigSection = ({ file, onChange, disabled }) => (
  <section className="device-create-section">
    <div className="device-create-section-title">
      <span className="device-step-number">3</span>
      <div>
        <h3>OID Config</h3>
        <p>Chọn file chứa danh sách OID cần giám sát.</p>
      </div>
    </div>

    <label className="device-oid-upload">
      <input
        type="file"
        accept=".xlsx,.xls,.csv"
        disabled={disabled}
        onChange={(e) => onChange(e.target.files?.[0] || null)}
      />

      <span className="device-oid-upload-icon">↑</span>

      <strong>{file ? file.name : 'Chọn file OID Config'}</strong>

      <span>
        {file
          ? `${(file.size / 1024).toFixed(1)} KB`
          : 'Nhấn để chọn file XLSX, XLS hoặc CSV'}
      </span>
    </label>
  </section>
);

const CreateDevicePage = () => {
  const navigate = useNavigate();

  const [isDarkMode, setIsDarkMode] = useState(
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );

  const [adding, setAdding] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const [deviceForm, setDeviceForm] = useState({
    deviceName: '',
    ipAddress: '',
    deviceType: '',
  });

  const [credentialForms, setCredentialForms] = useState([
    createDefaultCredential('SNMPV2C', 'SNMP_Credential_V2'),
    createDefaultCredential('SNMPV1', 'SNMP_Credential_V1'),
  ]);

  const [oidConfigFile, setOidConfigFile] = useState(null);

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

    const handleChange = (e) => setIsDarkMode(e.matches);

    mediaQuery.addEventListener('change', handleChange);

    return () => mediaQuery.removeEventListener('change', handleChange);
  }, []);

  const handleDeviceChange = (e) => {
    const { name, value } = e.target;

    setDeviceForm((prev) => ({ ...prev, [name]: value }));
    setErrorMsg('');
  };

  const handleAddCredential = () => {
    setCredentialForms((prev) => [
      ...prev,
      createDefaultCredential(
        'SNMPV2C',
        `SNMP_Credential_${prev.length + 1}`
      ),
    ]);
  };

  const handleRemoveCredential = (index) => {
    setCredentialForms((prev) => prev.filter((_, i) => i !== index));
  };

  const validateForm = () => {
    const { deviceName, ipAddress, deviceType } = deviceForm;

    if (!deviceName.trim() || !ipAddress.trim() || !deviceType.trim()) {
      return 'Vui lòng nhập đầy đủ thông tin thiết bị!';
    }

    if (!credentialForms.length) {
      return 'Vui lòng thêm ít nhất một Credential!';
    }

    for (const [index, credential] of credentialForms.entries()) {
      const number = index + 1;

      if (
        !credential.credentialName.trim() ||
        !credential.credentialType ||
        !credential.protocolType ||
        !credential.communityString.trim()
      ) {
        return `Vui lòng nhập đầy đủ thông tin Credential ${number}!`;
      }

      const port = Number(credential.port);

      if (!Number.isInteger(port) || port < 1 || port > 65535) {
        return `Port của Credential ${number} phải là số nguyên từ 1 đến 65535!`;
      }
    }

    const fileError = validateOidConfigFile(oidConfigFile);

    if (fileError) {
      return fileError;
    }

    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');

    const validationError = validateForm();

    if (validationError) {
      setErrorMsg(validationError);
      return;
    }

    let currentStep = 'Tạo thiết bị';

    try {
      setAdding(true);

      const deviceRes = await axiosClient.post('/devices', {
        deviceName: deviceForm.deviceName.trim(),
        ipAddress: deviceForm.ipAddress.trim(),
        deviceType: deviceForm.deviceType.trim(),
      });

      const deviceData = deviceRes.data?.data ?? deviceRes.data;
      const deviceId = deviceData?.id ?? deviceData?.deviceId;
      const numericDeviceId = Number(deviceId);

      if (!Number.isInteger(numericDeviceId) || numericDeviceId <= 0) {
        throw new Error(`Device ID không hợp lệ: ${deviceId ?? 'không có'}`);
      }

      for (const [index, credential] of credentialForms.entries()) {
        currentStep = `Tạo Credential ${index + 1}`;

        await axiosClient.post('/device-credentials', {
          deviceId: numericDeviceId,
          credentialName: credential.credentialName.trim(),
          credentialType: credential.credentialType,
          protocolType: credential.protocolType,
          communityString: credential.communityString.trim(),
          port: Number(credential.port),
          isActive: credential.isActive,
        });
      }

      currentStep = 'Import OID Config';

      const formData = new FormData();
      formData.append('file', oidConfigFile);

      await axiosClient.post('/oid-configs/import', formData, {
        params: {
          deviceId: numericDeviceId,
        },
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      alert(
        'Thêm thiết bị thành công!\n\n' +
          `Device ID: ${numericDeviceId}\n` +
          `Đã tạo ${credentialForms.length} Credential.\n` +
          'Đã import OID Config.'
      );

      navigate('/dashboard', {
        state: { refreshDevices: true },
      });
    } catch (err) {
      console.error(`Lỗi tại bước ${currentStep}:`, err);

      const message =
        err.response?.data?.message ||
        err.message ||
        'Đã xảy ra lỗi không xác định.';

      setErrorMsg(
        `${currentStep} thất bại!\n${message}\n\n` +
          'Lưu ý: Các bước đã hoàn thành trước đó không tự động được hoàn tác.'
      );
    } finally {
      setAdding(false);
    }
  };

  const handleCancel = () => {
    if (!adding) navigate(-1);
  };

  return (
    <div
      className={`register-page device-create-page ${
        isDarkMode ? 'dark-mode' : 'light-mode'
      }`}
    >
      <div className="register-card device-create-card">
        <div className="device-create-header">
          <button
            type="button"
            className="device-back-button"
            onClick={handleCancel}
            disabled={adding}
          >
            ←
          </button>

          <h2 className="register-title">THÊM THIẾT BỊ MỚI</h2>

          <p className="device-create-description">
            Thiết lập thông tin thiết bị, SNMP Credentials và danh sách OID
            giám sát.
          </p>
        </div>

        {errorMsg && (
          <div className="register-alert error device-error">
            <span style={{ whiteSpace: 'pre-line' }}>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="register-form">
          <DeviceInfoSection
            device={deviceForm}
            onChange={handleDeviceChange}
            disabled={adding}
          />

          <CredentialSection
            credentials={credentialForms}
            onChange={setCredentialForms}
            onAdd={handleAddCredential}
            onRemove={handleRemoveCredential}
            disabled={adding}
          />

          <OidConfigSection
            file={oidConfigFile}
            onChange={setOidConfigFile}
            disabled={adding}
          />

          <div className="register-actions">
            <button
              type="button"
              className="cancel-btn"
              onClick={handleCancel}
              disabled={adding}
            >
              Hủy
            </button>

            <button
              type="submit"
              className="submit-btn"
              disabled={adding}
            >
              {adding ? 'ĐANG TẠO THIẾT BỊ...' : '+ THÊM THIẾT BỊ'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateDevicePage;