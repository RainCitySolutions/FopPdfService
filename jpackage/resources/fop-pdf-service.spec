Summary: APPLICATION_SUMMARY
Name: APPLICATION_PACKAGE
Version: APPLICATION_VERSION
Release: APPLICATION_RELEASE
License: APPLICATION_LICENSE_TYPE
Vendor: APPLICATION_VENDOR

%global FopPdfUser fop-pdf-user

%if "x/opt" != "x"
Prefix: /opt
%endif

Provides: APPLICATION_PACKAGE

%if "x" != "x"
Group: 
%endif

Autoprov: 0
Autoreq: 0
%if "xalsa-lib, bzip2-libs, freetype, glibc, libX11, libXau, libXext, libXi, libXrender, libXtst, libpng, libxcb, zlib" != "x" || "x" != "x"
Requires: alsa-lib, bzip2-libs, freetype, glibc, libX11, libXau, libXext, libXi, libXrender, libXtst, libpng, libxcb, zlib 
%endif
Requires(pre): shadow-utils

%systemd_requires


#comment line below to enable effective jar compression
#it could easily get your package size from 40 to 15Mb but
#build time will substantially increase and it may require unpack200/system java to install
%define __jar_repack %{nil}

%define package_filelist %{_tmppath}/%{name}.files
%define app_filelist %{_tmppath}/%{name}.app.files
%define filesystem_filelist %{_tmppath}/%{name}.filesystem.files

%define default_filesystem / /opt /usr /usr/bin /usr/lib /usr/local /usr/local/bin /usr/local/lib

%description
APPLICATION_DESCRIPTION

%prep
echo App Desc - APPLICATION_DESCRIPTION
echo App Pkg  - APPLICATION_PACKAGE
echo Version  - APPLICATION_VERSION
echo Summary  - APPLICATION_SUMMARY
echo Release  - APPLICATION_RELEASE
echo License  - APPLICATION_LICENSE_TYPE
echo LicFile  - APPLICATION_LICENSE_FILE
echo Vendor   - APPLICATION_VENDOR
echo AppLnchr - APPLICATION_LAUNCHER
echo App Dir  - APPLICATION_DIRECTORY
echo Lnx Dir  - LINUX_INSTALL_DIR
echo defDeps  - PACKAGE_DEFAULT_DEPENDENCIES
echo custDeps - PACKAGE_CUSTOM_DEPENDENCIES
#echo Inst Dir - INSTALLATION_DIRECTORY
#echo App Usr  - APPLICATION_USER
#echo App Grp  - APPLICATION_GROUP
#echo AppFsName  APPLICATION_FS_NAME

%build
# Create the Service Unit file
cat > %{name}.service <<'EOF_SERVICE'
[Unit]
Description=APPLICATION_DESCRIPTION
After=network.target

[Service]
User=%{FopPdfUser}
Group=%{FopPdfUser}
Type=simple
ExecStart=APPLICATION_LAUNCHER
WorkingDirectory=APPLICATION_DIRECTORY
Restart=always
RestartSec=5
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=%n

[Install]
WantedBy=multi-user.target
EOF_SERVICE

# Create an empty override configuration file
cat > %{name}.properties <<'EOF_CONFIG'
# This file can be used to override the default configuration values
EOF_CONFIG


%install
# rm -rf %{buildroot}
#install -d -m 755 %{buildroot}/opt/%{name}
#install -d -m 755 %{buildroot}/opt/%{name}/log
#install -d -m 755 %{buildroot}/opt/%{name}/tmp

install -d -m 755 %{buildroot}APPLICATION_DIRECTORY
install -d -m 755 %{buildroot}APPLICATION_DIRECTORY/log
install -d -m 755 %{buildroot}APPLICATION_DIRECTORY/tmp

cp -r %{_sourcedir}/opt/%{name}/* %{buildroot}/opt/%{name}

# Remove files from the install that we don't want there. Generally these will be placed elsewhere.
rm %{buildroot}/opt/%{name}/lib/app/LICENSE
rm %{buildroot}/opt/%{name}/lib/app/README.md
rm %{buildroot}/opt/%{name}/lib/app/version.txt

# Find all of the directories in buildroot, remove any with leading periods, sort them and put them into app_filelist
(cd %{buildroot} && find . -type d) | sed -e 's/^\.//' -e '/^$/d' | sort > %{app_filelist}
# Ask RPM for the list of directories owned by filesystem, combine them with default_filesystem, sort them and put them into default_filesystem
{ rpm -ql filesystem || echo %{default_filesystem}; } | sort > %{filesystem_filelist}
# Compare the two lists, taking only the ones unquie to our app and put them into package_filelist
comm -23 %{app_filelist} %{filesystem_filelist} > %{package_filelist}
# Inject %dir in front of each directory in package_filelist
sed -i -e 's/.*/%dir "&"/' %{package_filelist}
# Find all files in buildroot, remove any with leading periods and append them to package_filelist 
(cd %{buildroot} && find . -not -type d) | sed -e 's/^\.//' -e 's/.*/"&"/' >> %{package_filelist}


# Remove the license file from package_filelist, included in the files section via %license
# sed -i -e 's|"%{license_install_file}"||' -e '/^$/d' %{package_filelist}

# Remove '%dir "/opt"' from package_filelist, resolves ownership issue on RHEL7
sed -i -e 's|%dir "/opt"||' -e '/^$/d' %{package_filelist}

# Put the license file in the system license dir and our root
%define license_install_file %{_defaultlicensedir}/%{name}-%{version}/%{basename:}
install -d -m 755 "%{buildroot}%{dirname:%{license_install_file}}"
install -m 644 %{_sourcedir}/opt/%{name}/lib/app/LICENSE "%{buildroot}%{license_install_file}"
install -m 644 %{_sourcedir}/opt/%{name}/lib/app/LICENSE "%{buildroot}/opt/%{name}/"

# Put the readme file in our root
%define doc_install_file /opt/%{name}/README.md
install -m 644 %{_sourcedir}/opt/%{name}/lib/app/README.md %{buildroot}%{doc_install_file}

# Copy the service unit file into place
mkdir -p %{buildroot}%{_unitdir}
install -m 644 %{name}.service %{buildroot}%{_unitdir}/%{name}.service

# Copy the initial properties file to our root
install -m 664 %{name}.properties %{buildroot}/opt/%{name}/%{name}.properties

# Copy version.txt into place
install -m 664 %{_sourcedir}/opt/%{name}/lib/app/version.txt %{buildroot}/opt/%{name}/


%files -f %{package_filelist}
%defattr(-,%{FopPdfUser}, %{FopPdfUser},-)

# Add the files that we generated ourselves (which won't be in package_filelist)
%{_unitdir}/%{name}.service

%config(noreplace) /opt/%{name}/%{name}.properties

/opt/%{name}/LICENSE
%license "%{license_install_file}" 
%doc %{doc_install_file}

/opt/%{name}/version.txt


%pre
getent passwd %{FopPdfUser} >/dev/null || \
    useradd -r -d /opt/%{name} -s /sbin/nologin \
    -c "User account for running the %{name}" %{FopPdfUser}
exit 0

%post
%systemd_post %{name}.service

%preun
%systemd_preun %{name}.service

%postun
%systemd_postun_with_restart %{name}.service

%clean
